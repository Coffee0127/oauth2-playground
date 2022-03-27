package io.github.coffee0127.oauth2.controller;

import io.github.coffee0127.oauth2.controller.utils.RedirectUtils;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.DefaultUriBuilderFactory;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/line")
public class LineController {

  private static final String LINE_LOGIN_STATE = "LineController.LINE_LOGIN_STATE";
  private static final String LINE_LOGIN_NONCE = "LineController.LINE_LOGIN_NONCE";
  private static final String AUTHORIZE_URL = "https://access.line.me/oauth2/v2.1/authorize";
  private static final String SCOPES = String.join(" ", List.of("openid", "profile"));

  @Value("${line.channelId}")
  private String channelId;

  @Value("${line.callbackUrl}")
  private String callbackUrl;

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
              var redirectUri =
                  new DefaultUriBuilderFactory(AUTHORIZE_URL)
                      .builder()
                      .queryParam("response_type", "code")
                      .queryParam("client_id", channelId)
                      .queryParam("redirect_uri", callbackUrl)
                      .queryParam("state", state)
                      .queryParam("scope", SCOPES)
                      .queryParam("nonce", nonce)
                      .build();
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
    return exchange
        .getSession()
        .flatMap(
            session -> {
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

              var sessionState = session.<String>getAttribute(LINE_LOGIN_STATE);
              if (StringUtils.isNotBlank(state) && !StringUtils.equals(state, sessionState)) {
                log.error(
                    "Mismatch state, parameter state : {}, session state : {}",
                    state,
                    sessionState);
                return RedirectUtils.redirect(exchange.getResponse(), "/login-failed.html");
              }

              session.getAttributes().remove(LINE_LOGIN_STATE);
              return RedirectUtils.redirect(exchange.getResponse(), "/");
            });
  }
}
