package io.github.coffee0127.oauth2.service;

import com.auth0.jwt.JWT;
import io.github.coffee0127.oauth2.objects.AccessTokenResponse;
import io.github.coffee0127.oauth2.objects.UserPrincipal;
import io.github.coffee0127.oauth2.service.client.LineClient;
import io.github.coffee0127.oauth2.service.dao.UserDao;
import java.net.URI;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@AllArgsConstructor
@Slf4j
@Service
public class LineService {
  private final LineClient client;

  private final UserDao userDao;

  public URI getRedirectUri(String state, String nonce) {
    return client.getRedirectUri(state, nonce);
  }

  public Mono<AccessTokenResponse> getAccessToken(String code) {
    return client.getAccessToken(code);
  }

  public Mono<Void> revoke(String accessToken) {
    return client.revoke(accessToken);
  }

  public boolean verifyIdToken(String idToken, String nonce) {
    return client.verifyIdToken(idToken, nonce);
  }

  public Mono<UserPrincipal> findUser(String idToken) {
    var jwt = JWT.decode(idToken);
    return Mono.just(
            new UserPrincipal(
                jwt.getClaim("iss").asString(),
                jwt.getClaim("sub").asString(),
                jwt.getClaim("aud").asString(),
                Instant.ofEpochSecond(jwt.getClaim("exp").asLong()),
                Instant.ofEpochSecond(jwt.getClaim("iat").asLong()),
                jwt.getClaim("nonce").asString(),
                jwt.getClaim("name").asString(),
                jwt.getClaim("picture").asString()))
        .flatMap(userDao::save);
  }
}
