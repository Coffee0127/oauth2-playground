package io.github.coffee0127.oauth2.service.dao;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.coffee0127.oauth2.objects.Registration;
import io.github.coffee0127.oauth2.objects.RegistrationKey;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class CaffeineRegistrationDao implements RegistrationDao {

  /** key is userId and value is registrations. */
  private final Cache<String, Map<RegistrationKey, Registration>> storage;

  public CaffeineRegistrationDao() {
    storage = Caffeine.newBuilder().build();
  }

  @Override
  public Mono<List<Registration>> findAllRegistrations() {
    return Mono.just(
        storage.asMap().values().stream()
            .map(Map::entrySet)
            .flatMap(Collection::stream)
            .map(Entry::getValue)
            .map(Registration::new)
            .collect(Collectors.toList()));
  }

  @Override
  public Mono<List<Registration>> findRegistrations(String userId) {
    return Mono.just(
        storage.asMap().entrySet().stream()
            .filter(entry -> entry.getKey().equals(userId))
            .map(Entry::getValue)
            .map(Map::entrySet)
            .flatMap(Collection::stream)
            .map(Entry::getValue)
            .map(Registration::new)
            .collect(Collectors.toList()));
  }

  @Override
  public Mono<Registration> saveRegistration(Registration registration) {
    return Mono.fromSupplier(
        () -> {
          var userId = registration.getRegistrationKey().getUserId();
          var registrations =
              Optional.ofNullable(storage.getIfPresent(userId)).orElseGet(HashMap::new);
          registration.setCreateTime(Instant.now());
          registrations.putIfAbsent(registration.getRegistrationKey(), registration);
          storage.put(userId, registrations);
          return registration;
        });
  }

  @Override
  public Mono<Registration> getRegistration(RegistrationKey registrationKey) {
    return Mono.justOrEmpty(
        Optional.ofNullable(storage.getIfPresent(registrationKey.getUserId()))
            .flatMap(registrations -> Optional.ofNullable(registrations.get(registrationKey))));
  }

  @Override
  public Mono<Void> deleteRegistration(RegistrationKey registrationKey) {
    return Mono.fromRunnable(
        () -> {
          var userId = registrationKey.getUserId();
          var registrations =
              Optional.ofNullable(storage.getIfPresent(userId)).orElseGet(Collections::emptyMap);
          registrations.remove(registrationKey);
          if (registrations.isEmpty()) {
            storage.invalidate(userId);
          } else {
            storage.put(userId, registrations);
          }
        });
  }
}
