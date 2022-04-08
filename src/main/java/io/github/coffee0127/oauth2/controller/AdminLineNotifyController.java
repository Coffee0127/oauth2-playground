package io.github.coffee0127.oauth2.controller;

import io.github.coffee0127.oauth2.controller.LineNotifyController.NotifyRequest;
import io.github.coffee0127.oauth2.controller.LineNotifyController.RegistrationResponse;
import io.github.coffee0127.oauth2.objects.Registration;
import io.github.coffee0127.oauth2.objects.RegistrationKey;
import io.github.coffee0127.oauth2.service.LineNotifyService;
import io.github.coffee0127.oauth2.service.UserService;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@AllArgsConstructor
@Slf4j
@RestController
@RequestMapping("/admin/api/registrations")
public class AdminLineNotifyController {
  private final LineNotifyService notifyService;

  private final UserService userService;

  @GetMapping
  public Mono<List<RegistrationResponse>> list() {
    return notifyService
        .findRegistrations()
        .flatMap(
            registrations ->
                Flux.fromIterable(registrations)
                    .parallel()
                    .runOn(Schedulers.parallel())
                    .flatMap(this::attachUserProfile)
                    .sequential()
                    .collectList());
  }

  private Mono<RegistrationResponse> attachUserProfile(Registration registration) {
    return userService
        .find(registration.getRegistrationKey().getUserId())
        .map(
            userPrincipal ->
                new RegistrationResponse()
                    .setUserId(registration.getRegistrationKey().getUserId())
                    .setUserName(userPrincipal.getName())
                    .setTargetType(registration.getRegistrationKey().getTargetType())
                    .setTarget(registration.getRegistrationKey().getTarget())
                    .setExpiryTime(registration.getExpiryTime().toEpochMilli()));
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Void>> notifyAll(
      @RequestBody Mono<List<NotifyRequest>> notifyRequests) {
    return notifyRequests
        .flatMapMany(Flux::fromIterable)
        .parallel()
        .runOn(Schedulers.parallel())
        .flatMap(
            notifyRequest -> {
              var registrationKey =
                  new RegistrationKey()
                      .setUserId(notifyRequest.getUserId())
                      .setTargetType(notifyRequest.getType())
                      .setTarget(notifyRequest.getTarget());
              return notifyService.notify(registrationKey, notifyRequest.getMsg());
            })
        .sequential()
        .collectList()
        .thenReturn(ResponseEntity.ok().build());
  }
}
