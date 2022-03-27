package io.github.coffee0127.oauth2.controller;

import io.github.coffee0127.oauth2.controller.utils.RedirectUtils;
import io.github.coffee0127.oauth2.service.LineService;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@AllArgsConstructor
@Slf4j
@RestController
@RequestMapping("/api/line")
public class LineController {

  private static final String LINE_LOGIN_STATE = "LineController.LINE_LOGIN_STATE";
  private static final String LINE_LOGIN_NONCE = "LineController.LINE_LOGIN_NONCE";
  private static final String LINE_ACCESS_TOKEN = "LineController.LINE_ACCESS_TOKEN";

  private final LineService lineService;

  @GetMapping("/login")
  public Mono<Void> login(ServerWebExchange exchange) {
    return exchange
        .getSession()
        .map(
            session -> {
              var state = UUID.randomUUID().toString();
              var nonce = UUID.randomUUID().toString();
              session.getAttributes().put(LINE_LOGIN_STATE, state);
              session.getAttributes().put(LINE_LOGIN_NONCE, nonce);
              var redirectUri = lineService.getRedirectUri(state, nonce);
              log.debug("redirectUri = {}", redirectUri);
              return redirectUri;
            })
        .flatMap(redirectUri -> RedirectUtils.redirect(exchange.getResponse(), redirectUri));
  }

  @GetMapping("/auth")
  public Mono<Void> auth(
      @RequestParam(value = "code", required = false) String code,
      @RequestParam(value = "state", required = false) String state,
      @RequestParam(value = "scope", required = false) String scope,
      @RequestParam(value = "error", required = false) String errorCode,
      @RequestParam(value = "error_description", required = false) String errorMessage,
      ServerWebExchange exchange) {
    if (log.isDebugEnabled()) {
      log.debug("parameter code : {}", code);
      log.debug("parameter state : {}", state);
      log.debug("parameter scope : {}", scope);
    }

    if (StringUtils.isNotBlank(errorCode) || StringUtils.isNotBlank(errorMessage)) {
      log.error("parameter errorCode : {}", errorCode);
      log.error("parameter errorMessage : {}", errorMessage);
      return RedirectUtils.redirect(exchange.getResponse(), "/login-failed.html");
    }

    return exchange
        .getSession()
        .flatMap(
            session -> {
              var sessionState = session.<String>getAttribute(LINE_LOGIN_STATE);
              if (StringUtils.isNotBlank(state) && !StringUtils.equals(state, sessionState)) {
                log.error(
                    "Mismatch state, parameter state : {}, session state : {}",
                    state,
                    sessionState);
                return RedirectUtils.redirect(exchange.getResponse(), "/login-failed.html");
              }

              session.getAttributes().remove(LINE_LOGIN_STATE);
              return lineService
                  .getAccessToken(code)
                  .map(
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
                        return token;
                      })
                  .then(RedirectUtils.redirect(exchange.getResponse(), "/"));
            });
  }
}