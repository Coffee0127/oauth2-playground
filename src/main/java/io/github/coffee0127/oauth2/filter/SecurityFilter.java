package io.github.coffee0127.oauth2.filter;

import io.github.coffee0127.oauth2.controller.LineController;
import io.github.coffee0127.oauth2.controller.utils.RedirectUtils;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class SecurityFilter implements WebFilter {

  private static final Set<String> EXCLUDES =
      Set.of(
          "/(css|img|js)/.*",
          "/login",
          "/unauthorized",
          "/api/line/(login|auth)",
          "/api/registrations/auth");

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    var path = exchange.getRequest().getPath();
    for (String exclude : EXCLUDES) {
      if (path.value().matches(exclude)) {
        return chain.filter(exchange);
      }
    }
    return exchange
        .getSession()
        .flatMap(
            session -> {
              if (session.getAttribute(LineController.LINE_ID_TOKEN) == null) {
                log.warn("Unauthorized to access {}", path);
                return RedirectUtils.redirect(exchange.getResponse(), "/unauthorized");
              }
              return chain.filter(exchange);
            });
  }
}
