package io.github.coffee0127.oauth2.service;

import io.github.coffee0127.oauth2.objects.Registration;
import io.github.coffee0127.oauth2.service.client.LineNotifyClient;
import io.github.coffee0127.oauth2.service.dao.RegistrationDao;
import java.time.Duration;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ScheduleManager {

  private final Timer timer;
  private final RegistrationDao dao;
  private final LineNotifyClient lineNotifyClient;

  public ScheduleManager(RegistrationDao dao, LineNotifyClient lineNotifyClient) {
    this.dao = dao;
    this.lineNotifyClient = lineNotifyClient;
    this.timer = new Timer();
  }

  public void scheduleCleanup(Registration registration) {
    var expiryTime = registration.getCreateTime().plus(Duration.ofHours(1));
    registration.setExpiryTime(expiryTime);
    log.info("Schedule cleanup for {} at {}", registration.getRegistrationKey(), expiryTime);
    timer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            log.info("Cleanup for {}", registration.getRegistrationKey());
            lineNotifyClient
                .revokeAccessToken(registration.getAccessToken())
                .retry(5)
                // TODO enhance retry mechanism
                .doOnError(throwable -> log.error("Revoke access token failed..."))
                .then(dao.delete(registration.getRegistrationKey()))
                .block();
          }
        },
        Date.from(expiryTime));
  }
}
