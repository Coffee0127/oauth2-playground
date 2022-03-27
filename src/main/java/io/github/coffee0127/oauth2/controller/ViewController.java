package io.github.coffee0127.oauth2.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.WebSession;

@Slf4j
@Controller
public class ViewController {

  @GetMapping("/")
  public String index(WebSession session, Model model) {
    var idToken = session.getAttribute(LineController.LINE_ID_TOKEN);
    if (idToken == null) {
      log.warn("User hasn't logged in yet.");
      return "redirect:/login.html";
    }

    model.addAttribute("idToken", idToken);
    return "users/index";
  }
}
