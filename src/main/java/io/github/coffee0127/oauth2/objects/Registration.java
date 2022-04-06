package io.github.coffee0127.oauth2.objects;

import java.time.Instant;
import lombok.Data;

@Data
public class Registration {

  private final RegistrationKey registrationKey;

  private final String accessToken;

  private Instant createTime;

  public Registration(RegistrationKey registrationKey, String accessToken) {
    this.registrationKey = registrationKey;
    this.accessToken = accessToken;
  }

  public Registration(Registration registration) {
    this.registrationKey = new RegistrationKey(registration.registrationKey);
    this.accessToken = registration.accessToken;
    this.createTime = registration.createTime;
  }
}
