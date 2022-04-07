package io.github.coffee0127.oauth2.controller;

import io.github.coffee0127.oauth2.constant.OAuth2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.WebSession;

@Slf4j
@Controller
public class ViewController {

  private static final String VIEW_USERS_LOGIN = "users/login";
  private static final String REDIRECT_TO_INDEX = "redirect:/";

  @GetMapping("/")
  public String index(WebSession session, Model model) {
    return processView(session, model, "users/index");
  }

  @GetMapping("/login")
  public String login(WebSession session) {
    if (session.getAttribute(OAuth2.USER_PRINCIPAL) != null) {
      return REDIRECT_TO_INDEX;
    }
    return VIEW_USERS_LOGIN;
  }

  @GetMapping("/unauthorized")
  public String unauthorized(WebSession session) {
    if (session.getAttribute(OAuth2.USER_PRINCIPAL) != null) {
      return REDIRECT_TO_INDEX;
    }
    return "unauthorized";
  }

  @GetMapping("/line-notify")
  public String lineNotify(WebSession session, Model model) {
    return processView(session, model, "users/line-notify");
  }

  private String processView(WebSession session, Model model, String dest) {
    var userPrincipal = session.getAttribute(OAuth2.USER_PRINCIPAL);
    if (userPrincipal == null) {
      log.warn("User hasn't logged in yet.");
      return VIEW_USERS_LOGIN;
    }

    model.addAttribute("userPrincipal", userPrincipal);
    return dest;
  }
}
