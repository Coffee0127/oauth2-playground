package io.github.coffee0127.oauth2.service;

import io.github.coffee0127.oauth2.objects.UserPrincipal;
import io.github.coffee0127.oauth2.service.dao.UserDao;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@AllArgsConstructor
@Service
public class UserService {

  private final UserDao dao;

  public Mono<UserPrincipal> find(String userId){
    return dao.find(userId);
  }
}
