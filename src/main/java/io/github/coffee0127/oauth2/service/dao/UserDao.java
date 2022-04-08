package io.github.coffee0127.oauth2.service.dao;

import io.github.coffee0127.oauth2.objects.UserPrincipal;
import reactor.core.publisher.Mono;

public interface UserDao {
  Mono<UserPrincipal> find(String userId);

  Mono<UserPrincipal> save(UserPrincipal userPrincipal);
}
