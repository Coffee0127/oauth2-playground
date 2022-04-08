package io.github.coffee0127.oauth2.service.dao.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.coffee0127.oauth2.objects.UserPrincipal;
import io.github.coffee0127.oauth2.service.dao.UserDao;
import java.util.Optional;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class UserCaffeineDao implements UserDao {

  /** key is userId and value is userPrincipal. */
  private final Cache<String, UserPrincipal> storage;

  public UserCaffeineDao() {
    storage = Caffeine.newBuilder().build();
  }

  @Override
  public Mono<UserPrincipal> find(String userId) {
    return Mono.justOrEmpty(Optional.ofNullable(storage.getIfPresent(userId)));
  }

  @Override
  public Mono<UserPrincipal> save(UserPrincipal userPrincipal) {
    return Mono.fromSupplier(
        () -> {
          storage.put(userPrincipal.getUserId(), userPrincipal);
          return userPrincipal;
        });
  }

  @Override
  public Mono<Void> delete(String userId) {
    return Mono.fromRunnable(() -> storage.invalidate(userId));
  }
}
