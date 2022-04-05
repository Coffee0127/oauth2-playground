package io.github.coffee0127.oauth2.objects;

import lombok.Data;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@Data
public class RegistrationKey {

  private String userId;

  private String targetType;

  private String target;

  public RegistrationKey() {}

  public RegistrationKey(RegistrationKey registrationKey) {
    this.userId = registrationKey.userId;
    this.targetType = registrationKey.targetType;
    this.target = registrationKey.target;
  }
}
