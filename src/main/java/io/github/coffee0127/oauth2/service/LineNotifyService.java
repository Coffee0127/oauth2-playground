package io.github.coffee0127.oauth2.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.github.coffee0127.oauth2.objects.AccessTokenResponse;
import io.github.coffee0127.oauth2.objects.Registration;
import io.github.coffee0127.oauth2.objects.RegistrationKey;
import io.github.coffee0127.oauth2.service.dao.RegistrationDao;
import io.netty.channel.ChannelOption;
import io.netty.handler.codec.http.HttpHeaderNames;
import java.net.URI;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@Slf4j
@Service
public class LineNotifyService {
  private static final String OAUTH_BASE_URL = "https://notify-bot.line.me/oauth";
  private static final String NOTIFICATION_BASE_URL = "https://notify-api.line.me/api";
  private static final String USER_AGENT = "oauth2-playground-client";
  private final WebClient oauthWebClient;
  private final WebClient notificationWebClient;
  private final RegistrationDao dao;

  @Value("${line.notify.clientId}")
  private String clientId;

  @Value("${line.notify.clientSecret}")
  private String clientSecret;

  @Value("${line.notify.callbackUrl}")
  private String callbackUrl;

  public LineNotifyService(
      WebClient.Builder webClientBuilder, ObjectMapper baseObjectMapper, RegistrationDao dao) {
    var objectMapper = baseObjectMapper.copy();
    objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    this.oauthWebClient =
        webClientBuilder
            .clone()
            .exchangeStrategies(
                ExchangeStrategies.builder()
                    .codecs(
                        configurer ->
                            configurer
                                .customCodecs()
                                .registerWithDefaultConfig(new Jackson2JsonDecoder(objectMapper)))
                    .build())
            .clientConnector(createConnector(OAUTH_BASE_URL))
            .build();
    this.notificationWebClient =
        webClientBuilder.clone().clientConnector(createConnector(NOTIFICATION_BASE_URL)).build();
    this.dao = dao;
  }

  private ReactorClientHttpConnector createConnector(String oauthBaseUrl) {
    return new ReactorClientHttpConnector(
        HttpClient.create()
            .baseUrl(oauthBaseUrl)
            .keepAlive(true)
            .followRedirect(true)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            .headers(builder -> builder.add(HttpHeaderNames.USER_AGENT, USER_AGENT)));
  }

  public Mono<List<Registration>> findRegistrations(String userId) {
    return dao.findRegistrations(userId);
  }

  public URI getRedirectUri(String state) {
    return new DefaultUriBuilderFactory(OAUTH_BASE_URL + "/authorize")
        .builder()
        .queryParam("response_type", "code")
        .queryParam("client_id", clientId)
        .queryParam("redirect_uri", callbackUrl)
        .queryParam("state", state)
        .queryParam("scope", "notify")
        .queryParam("response_mode", "form_post")
        .build();
  }

  public Mono<Void> register(String userId, String code) {
    return getAccessToken(code)
        .doOnSuccess(accessToken -> log.debug("Get {} accessToken {}", userId, accessToken))
        .flatMap(
            accessToken ->
                getStatus(accessToken)
                    .map(
                        statusResponse ->
                            new Registration(
                                new RegistrationKey()
                                    .setUserId(userId)
                                    .setTargetType(statusResponse.getTargetType())
                                    .setTarget(statusResponse.getTarget()),
                                accessToken))
                    .flatMap(dao::saveRegistration));
  }

  private Mono<String> getAccessToken(String code) {
    var formData = new LinkedMultiValueMap<>();
    formData.add("grant_type", "authorization_code");
    formData.add("client_id", clientId);
    formData.add("client_secret", clientSecret);
    formData.add("redirect_uri", callbackUrl);
    formData.add("code", code);
    return oauthWebClient
        .post()
        .uri("/token")
        .bodyValue(formData)
        .retrieve()
        .bodyToMono(AccessTokenResponse.class)
        .map(AccessTokenResponse::getAccessToken)
        .doOnError(throwable -> log.error(throwable.getMessage(), throwable));
  }

  private Mono<StatusResponse> getStatus(String accessToken) {
    return notificationWebClient
        .get()
        .uri("/status")
        .header(HttpHeaders.AUTHORIZATION, formatAccessToken(accessToken))
        .retrieve()
        .bodyToMono(StatusResponse.class)
        .doOnError(throwable -> log.error(throwable.getMessage(), throwable));
  }

  public Mono<Void> notify(RegistrationKey registrationKey, String message) {
    return dao.getRegistration(registrationKey)
        .map(Registration::getAccessToken)
        .flatMap(accessToken -> notify(accessToken, message))
        .then();
  }

  private Mono<String> notify(String accessToken, String message) {
    var formData = new LinkedMultiValueMap<>();
    formData.add("message", message);
    return notificationWebClient
        .post()
        .uri("/notify")
        .header(HttpHeaders.AUTHORIZATION, formatAccessToken(accessToken))
        .bodyValue(formData)
        .retrieve()
        .bodyToMono(BasicResponse.class)
        .filter(response -> HttpStatus.OK.value() == response.getStatus())
        .map(BasicResponse::getMessage)
        .doOnError(throwable -> log.error(throwable.getMessage(), throwable));
  }

  public Mono<Void> revoke(RegistrationKey registrationKey) {
    return dao.getRegistration(registrationKey)
        .map(Registration::getAccessToken)
        .flatMap(this::revokeAccessToken)
        .flatMap(unused -> dao.deleteRegistration(registrationKey));
  }

  private Mono<String> revokeAccessToken(String accessToken) {
    return notificationWebClient
        .post()
        .uri("/revoke")
        .header(HttpHeaders.AUTHORIZATION, formatAccessToken(accessToken))
        .retrieve()
        .bodyToMono(BasicResponse.class)
        .filter(response -> HttpStatus.OK.value() == response.getStatus())
        .map(BasicResponse::getMessage)
        .doOnError(throwable -> log.error(throwable.getMessage(), throwable));
  }

  private String formatAccessToken(String accessToken) {
    return "Bearer " + accessToken;
  }

  @Data
  private static class BasicResponse {
    private Integer status;
    private String message;
  }

  @EqualsAndHashCode(callSuper = true)
  @Data
  private static class StatusResponse extends BasicResponse {
    private String targetType;
    private String target;
  }
}
