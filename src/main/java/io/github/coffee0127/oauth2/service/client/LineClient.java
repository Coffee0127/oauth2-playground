package io.github.coffee0127.oauth2.service.client;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.github.coffee0127.oauth2.objects.AccessTokenResponse;
import io.netty.channel.ChannelOption;
import io.netty.handler.codec.http.HttpHeaderNames;
import java.net.URI;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@Slf4j
@Component
public class LineClient {

  private static final String OAUTH_BASE_URL = "https://api.line.me/oauth2/v2.1";
  private static final String AUTHORIZE_URL = "https://access.line.me/oauth2/v2.1/authorize";
  private static final String USER_AGENT = "oauth2-playground-client";
  private static final String SCOPES = String.join(" ", List.of("openid", "profile"));
  private final WebClient webClient;

  @Value("${line.channelId}")
  private String channelId;

  @Value("${line.channelSecret}")
  private String channelSecret;

  @Value("${line.callbackUrl}")
  private String callbackUrl;

  public LineClient(WebClient.Builder webClientBuilder, ObjectMapper baseObjectMapper) {
    var objectMapper = baseObjectMapper.copy();
    objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    this.webClient =
        webClientBuilder
            .exchangeStrategies(
                ExchangeStrategies.builder()
                    .codecs(
                        configurer ->
                            configurer
                                .customCodecs()
                                .registerWithDefaultConfig(new Jackson2JsonDecoder(objectMapper)))
                    .build())
            .clientConnector(
                new ReactorClientHttpConnector(
                    HttpClient.create()
                        .baseUrl(OAUTH_BASE_URL)
                        .keepAlive(true)
                        .followRedirect(true)
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                        .headers(builder -> builder.add(HttpHeaderNames.USER_AGENT, USER_AGENT))))
            .build();
  }

  public URI getRedirectUri(String state, String nonce) {
    return new DefaultUriBuilderFactory(AUTHORIZE_URL)
        .builder()
        .queryParam("response_type", "code")
        .queryParam("client_id", channelId)
        .queryParam("redirect_uri", callbackUrl)
        .queryParam("state", state)
        .queryParam("scope", SCOPES)
        .queryParam("nonce", nonce)
        .build();
  }

  public Mono<AccessTokenResponse> getAccessToken(String code) {
    var formData = new LinkedMultiValueMap<>();
    formData.add("grant_type", "authorization_code");
    formData.add("client_id", channelId);
    formData.add("client_secret", channelSecret);
    formData.add("redirect_uri", callbackUrl);
    formData.add("code", code);
    return webClient
        .post()
        .uri("/token")
        .bodyValue(formData)
        .retrieve()
        .bodyToMono(AccessTokenResponse.class)
        .doOnError(throwable -> log.error(throwable.getMessage(), throwable));
  }

  public Mono<Void> revoke(String accessToken) {
    var formData = new LinkedMultiValueMap<>();
    formData.add("access_token", accessToken);
    formData.add("client_id", channelId);
    formData.add("client_secret", channelSecret);
    return webClient
        .post()
        .uri("/revoke")
        .bodyValue(formData)
        .retrieve()
        .bodyToMono(Void.class)
        .doOnError(throwable -> log.error(throwable.getMessage(), throwable));
  }

  public boolean verifyIdToken(String idToken, String nonce) {
    try {
      JWT.require(Algorithm.HMAC256(channelSecret))
          .withIssuer("https://access.line.me")
          .withAudience(channelId)
          .withClaim("nonce", nonce)
          // add 60 seconds leeway to handle clock skew between client and server sides.
          .acceptLeeway(60)
          .build()
          .verify(idToken);
      return true;
    } catch (JWTVerificationException e) {
      // Invalid signature/claims
      return false;
    }
  }
}
