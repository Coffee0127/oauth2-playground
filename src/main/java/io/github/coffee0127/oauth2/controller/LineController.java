package io.github.coffee0127.oauth2.controller;

import io.github.coffee0127.oauth2.constant.ErrorCode;
import io.github.coffee0127.oauth2.constant.OAuth2;
import io.github.coffee0127.oauth2.controller.utils.RedirectUtils;
import io.github.coffee0127.oauth2.objects.AccessTokenResponse;
import io.github.coffee0127.oauth2.objects.Registration;
import io.github.coffee0127.oauth2.objects.UserPrincipal;
import io.github.coffee0127.oauth2.service.LineNotifyService;
import io.github.coffee0127.oauth2.service.LineService;
import java.util.Optional;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@AllArgsConstructor
@Slf4j
@RestController
@RequestMapping("/api/line")
public class LineController {

  private static final String LINE_LOGIN_STATE = "LineController.LINE_LOGIN_STATE";
  private static final String LINE_LOGIN_NONCE = "LineController.LINE_LOGIN_NONCE";
  private static final String LINE_ACCESS_TOKEN = "LineController.LINE_ACCESS_TOKEN";

  private final LineService lineService;

  private final LineNotifyService lineNotifyService;

  @GetMapping("/login")
  public Mono<Void> login(WebSession session, ServerHttpResponse response) {
    var state = UUID.randomUUID().toString();
    var nonce = UUID.randomUUID().toString();
    session.getAttributes().put(LINE_LOGIN_STATE, state);
    session.getAttributes().put(LINE_LOGIN_NONCE, nonce);
    var redirectUri = lineService.getRedirectUri(state, nonce);
    log.debug("redirectUri = {}", redirectUri);
    return RedirectUtils.redirect(response, redirectUri);
  }

  @GetMapping("/logout")
  public Mono<Void> logout(WebSession session, ServerHttpResponse response) {
    return Mono.justOrEmpty(
            Optional.ofNullable(session.<AccessTokenResponse>getAttribute(LINE_ACCESS_TOKEN))
                .map(AccessTokenResponse::getAccessToken))
        .flatMap(lineService::revoke)
        .then(session.invalidate())
        .then(RedirectUtils.redirect(response, "/login"));
  }

  @GetMapping("/cleanUp")
  public Mono<Void> cleanup(WebSession session, ServerHttpResponse response) {
    return Mono.justOrEmpty(
            Optional.ofNullable(session.<AccessTokenResponse>getAttribute(LINE_ACCESS_TOKEN))
                .map(AccessTokenResponse::getAccessToken))
        .flatMap(lineService::revoke)
        .then(lineNotifyService.findRegistrations(getUserId(session)))
        .flatMapMany(Flux::fromIterable)
        .parallel()
        .runOn(Schedulers.parallel())
        .map(Registration::getRegistrationKey)
        .flatMap(lineNotifyService::revoke)
        .sequential()
        .collectList()
        .then(session.invalidate())
        .then(RedirectUtils.redirect(response, "/login"));
  }

  private String getUserId(WebSession session) {
    return Optional.ofNullable(session.<UserPrincipal>getAttribute(OAuth2.USER_PRINCIPAL))
        .map(UserPrincipal::getUserId)
        .orElseThrow(() -> new IllegalStateException("Cannot find User ID from session."));
  }

  @GetMapping("/auth")
  public Mono<Void> auth(
      @RequestParam(value = "code", required = false) String code,
      @RequestParam(value = "state", required = false) String state,
      @RequestParam(value = "scope", required = false) String scope,
      @RequestParam(value = "error", required = false) String errorCode,
      @RequestParam(value = "error_description", required = false) String errorMessage,
      WebSession session,
      ServerHttpResponse response) {
    if (log.isDebugEnabled()) {
      log.debug("parameter code : {}", code);
      log.debug("parameter state : {}", state);
      log.debug("parameter scope : {}", scope);
    }

    if (StringUtils.isNotBlank(errorCode) || StringUtils.isNotBlank(errorMessage)) {
      log.error("parameter errorCode : {}", errorCode);
      log.error("parameter errorMessage : {}", errorMessage);
      return RedirectUtils.redirect(response, createRedirectUri(ErrorCode.LOGIN_FAILED_CALLBACK));
    }

    var sessionState = session.<String>getAttribute(LINE_LOGIN_STATE);
    if (StringUtils.isNotBlank(state) && !StringUtils.equals(state, sessionState)) {
      log.error("Mismatch state, parameter state : {}, session state : {}", state, sessionState);
      return RedirectUtils.redirect(
          response, createRedirectUri(ErrorCode.LOGIN_FAILED_MISMATCH_STATE));
    }

    session.getAttributes().remove(LINE_LOGIN_STATE);
    return lineService
        .getAccessToken(code)
        .flatMap(
            token -> {
              if (log.isDebugEnabled()) {
                log.debug("scope : {}", token.getScope());
                log.debug("access_token : {}", token.getAccessToken());
                log.debug("token_type : {}", token.getTokenType());
                log.debug("expires_in : {}", token.getExpiresIn());
                log.debug("refresh_token : {}", token.getRefreshToken());
                log.debug("id_token : {}", token.getIdToken());
              }
              session.getAttributes().put(LINE_ACCESS_TOKEN, token);
              return extractUserProfile(session, response, token);
            });
  }

  private String createRedirectUri(ErrorCode errorCode) {
    return "/login?error=" + errorCode.getCode();
  }

  private Mono<Void> extractUserProfile(
      WebSession session, ServerHttpResponse response, AccessTokenResponse accessToken) {
    if (!lineService.verifyIdToken(
        accessToken.getIdToken(), session.getAttribute(LINE_LOGIN_NONCE))) {
      log.error("id_token is invalid");
      return RedirectUtils.redirect(
          response, createRedirectUri(ErrorCode.LOGIN_FAILED_INVALID_ID_TOKEN));
    }

    session.getAttributes().remove(LINE_LOGIN_NONCE);
    return lineService
        .findUser(accessToken.getIdToken())
        .doOnSuccess(
            userPrincipal -> {
              if (log.isDebugEnabled()) {
                log.debug("userId : {}", userPrincipal.getUserId());
                log.debug("displayName : {}", userPrincipal.getName());
                log.debug("pictureUrl : {}", userPrincipal.getPicture());
              }
            })
        .flatMap(
            userPrincipal -> {
              session.getAttributes().put(OAuth2.USER_PRINCIPAL, userPrincipal);
              return RedirectUtils.redirect(response, "/");
            });
  }
}
