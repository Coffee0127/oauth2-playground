package io.github.coffee0127.oauth2.service.dao;

import io.github.coffee0127.oauth2.objects.Registration;
import io.github.coffee0127.oauth2.objects.RegistrationKey;
import java.util.List;
import reactor.core.publisher.Mono;

public interface RegistrationDao {

  Mono<List<Registration>> findAllRegistrations();

  Mono<List<Registration>> findRegistrations(String userId);

  Mono<Registration> saveRegistration(Registration registration);

  Mono<Registration> getRegistration(RegistrationKey registrationKey);

  Mono<Void> deleteRegistration(RegistrationKey registrationKey);
}
