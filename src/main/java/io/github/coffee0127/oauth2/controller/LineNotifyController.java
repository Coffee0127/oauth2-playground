package io.github.coffee0127.oauth2.controller;

import io.github.coffee0127.oauth2.constant.ErrorCode;
import io.github.coffee0127.oauth2.constant.OAuth2;
import io.github.coffee0127.oauth2.controller.utils.RedirectUtils;
import io.github.coffee0127.oauth2.objects.RegistrationKey;
import io.github.coffee0127.oauth2.objects.UserPrincipal;
import io.github.coffee0127.oauth2.service.LineNotifyService;
import io.github.coffee0127.oauth2.service.ScheduleManager;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

@AllArgsConstructor
@Slf4j
@RestController
@RequestMapping("/api/registrations")
public class LineNotifyController {
  private static final String LINE_NOTIFY_STATE = "LineNotifyController.LINE_NOTIFY_STATE";

  // TODO temp workaround for resolving state mismatched when using `form_post` response.
  //  key is previous state, value is userId.
  private static final Map<String, String> STATES = new HashMap<>();

  private final LineNotifyService notifyService;
  private final ScheduleManager scheduleManager;

  @GetMapping
  public Mono<List<RegistrationResponse>> list(WebSession session) {
    return notifyService
        .findRegistrations(getUserId(session))
        .map(
            registrations ->
                registrations.stream()
                    .map(
                        registration ->
                            new RegistrationResponse()
                                .setTargetType(registration.getRegistrationKey().getTargetType())
                                .setTarget(registration.getRegistrationKey().getTarget())
                                .setExpiryTime(registration.getExpiryTime().toEpochMilli()))
                    .collect(Collectors.toList()));
  }

  @GetMapping("/register")
  public Mono<Void> register(WebSession session, ServerHttpResponse response) {
    var state = UUID.randomUUID().toString();
    session.getAttributes().put(LINE_NOTIFY_STATE, state);
    STATES.put(state, getUserId(session));
    var redirectUri = notifyService.getRedirectUri(state);
    log.debug("redirectUri = {}", redirectUri);
    return RedirectUtils.redirect(response, redirectUri);
  }

  @GetMapping("/auth")
  public Mono<Void> auth(
      @RequestParam(value = "code", required = false) String code,
      @RequestParam(value = "state", required = false) String state,
      @RequestParam(value = "error", required = false) String errorCode,
      @RequestParam(value = "error_description", required = false) String errorMessage,
      WebSession session,
      ServerHttpResponse response) {
    if (log.isDebugEnabled()) {
      log.debug("parameter code : {}", code);
      log.debug("parameter state : {}", state);
    }

    if (StringUtils.isNotBlank(errorCode) || StringUtils.isNotBlank(errorMessage)) {
      log.error("parameter errorCode : {}", errorCode);
      log.error("parameter errorMessage : {}", errorMessage);
      return RedirectUtils.redirect(response, createErrorRedirectUri(errorMessage));
    }

    var prevState = session.<String>getAttribute(LINE_NOTIFY_STATE);
    var matchSessionState = StringUtils.isNotBlank(state) && StringUtils.equals(state, prevState);
    if (!STATES.containsKey(state) && !matchSessionState) {
      log.error("Mismatch state, parameter state : {}, previous state : {}", state, prevState);
      return RedirectUtils.redirect(response, createErrorRedirectUri());
    }

    session.getAttributes().remove(LINE_NOTIFY_STATE);
    return notifyService
        .register(STATES.get(state), code)
        .doOnSuccess(scheduleManager::scheduleCleanup)
        .then(Mono.fromRunnable(() -> STATES.remove(state)))
        .then(RedirectUtils.redirect(response, "/line-notify"));
  }

  @PostMapping("/auth")
  public Mono<Void> authByPost(
      WebSession session, ServerWebExchange exchange, ServerHttpResponse response) {
    return exchange
        .getFormData()
        .flatMap(
            formData -> {
              var code = formData.getFirst("code");
              var state = formData.getFirst("state");
              var errorCode = formData.getFirst("error");
              var errorMessage = formData.getFirst("error_description");
              return auth(code, state, errorCode, errorMessage, session, response);
            });
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Void>> notify(
      @RequestBody Mono<NotifyRequest> notifyRequest, WebSession session) {
    return notifyRequest
        .flatMap(
            request -> {
              var registrationKey =
                  new RegistrationKey()
                      .setUserId(getUserId(session))
                      .setTargetType(request.getType())
                      .setTarget(request.getTarget());
              return notifyService.notify(registrationKey, request.getMsg());
            })
        .thenReturn(ResponseEntity.ok().build());
  }

  @DeleteMapping
  public Mono<ResponseEntity<Void>> revoke(
      @RequestHeader("type") String type,
      @RequestHeader("target") String target,
      WebSession session) {
    var registrationKey =
        new RegistrationKey().setUserId(getUserId(session)).setTargetType(type).setTarget(target);
    log.info("{} revokes registration for {}-{}", registrationKey.getUserId(), type, target);
    return notifyService.revoke(registrationKey).map(unused -> ResponseEntity.noContent().build());
  }

  private String getUserId(WebSession session) {
    return Optional.ofNullable(session.<UserPrincipal>getAttribute(OAuth2.USER_PRINCIPAL))
        .map(UserPrincipal::getUserId)
        .orElseThrow(() -> new IllegalStateException("Cannot find User ID from session."));
  }

  private String createErrorRedirectUri() {
    return "/line-notify?error=" + ErrorCode.LOGIN_FAILED_MISMATCH_STATE.getCode();
  }

  private String createErrorRedirectUri(String errorMessage) {
    return createErrorRedirectUri()
        + "&errorMsg="
        + URLEncoder.encode(errorMessage, StandardCharsets.UTF_8);
  }

  @Data
  static class NotifyRequest {
    private String type;
    private String target;
    private String msg;
    private String userId;
  }

  @Accessors(chain = true)
  @Data
  static class RegistrationResponse {
    private String targetType;
    private String target;
    private Long expiryTime;
    private String userId;
    private String userName;
  }
}
