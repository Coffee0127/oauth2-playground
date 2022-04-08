package io.github.coffee0127.oauth2.service;

import io.github.coffee0127.oauth2.objects.Registration;
import io.github.coffee0127.oauth2.objects.RegistrationKey;
import io.github.coffee0127.oauth2.service.client.LineNotifyClient;
import io.github.coffee0127.oauth2.service.dao.RegistrationDao;
import java.net.URI;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@AllArgsConstructor
@Slf4j
@Service
public class LineNotifyService {

  private final LineNotifyClient client;
  private final RegistrationDao dao;

  public Mono<List<Registration>> findRegistrations() {
    return dao.findAll();
  }

  public Mono<List<Registration>> findRegistrations(String userId) {
    return dao.find(userId);
  }

  public URI getRedirectUri(String state) {
    return client.getRedirectUri(state);
  }

  public Mono<Registration> register(String userId, String code) {
    return client
        .getAccessToken(code)
        .doOnSuccess(accessToken -> log.debug("Get {} accessToken {}", userId, accessToken))
        .flatMap(
            accessToken ->
                client
                    .getStatus(accessToken)
                    .map(
                        registrationKey ->
                            new Registration(registrationKey.setUserId(userId), accessToken))
                    .flatMap(dao::save));
  }

  public Mono<Void> notify(RegistrationKey registrationKey, String message) {
    return dao.findOne(registrationKey)
        .map(Registration::getAccessToken)
        .flatMap(accessToken -> client.notify(accessToken, message))
        .then();
  }

  public Mono<Void> revoke(RegistrationKey registrationKey) {
    return dao.findOne(registrationKey)
        .map(Registration::getAccessToken)
        .flatMap(client::revokeAccessToken)
        .flatMap(unused -> dao.delete(registrationKey));
  }
}
