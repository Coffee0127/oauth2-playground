package io.github.coffee0127.oauth2.controller.utils;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Mono;

public class RedirectUtils {

  public static Mono<Void> redirect(ServerHttpResponse response, String uri) {
    return redirect(response, URI.create(uri));
  }

  public static Mono<Void> redirect(ServerHttpResponse response, URI uri) {
    response.setStatusCode(HttpStatus.FOUND);
    response.getHeaders().setLocation(uri);
    return response.setComplete();
  }
}
