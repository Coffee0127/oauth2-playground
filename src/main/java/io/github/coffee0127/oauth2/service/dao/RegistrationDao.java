package io.github.coffee0127.oauth2.service.dao;

import io.github.coffee0127.oauth2.objects.Registration;
import io.github.coffee0127.oauth2.objects.RegistrationKey;
import java.util.List;
import reactor.core.publisher.Mono;

public interface RegistrationDao {

  Mono<List<Registration>> findAll();

  Mono<List<Registration>> find(String userId);

  Mono<Registration> findOne(RegistrationKey registrationKey);

  Mono<Registration> save(Registration registration);

  Mono<Void> delete(RegistrationKey registrationKey);
}
