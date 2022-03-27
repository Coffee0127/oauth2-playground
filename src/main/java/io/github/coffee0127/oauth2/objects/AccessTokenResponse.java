package io.github.coffee0127.oauth2.objects;

import lombok.Data;

@Data
public class AccessTokenResponse {
  private String scope;
  private String accessToken;
  private String tokenType;
  private Integer expiresIn;
  private String refreshToken;
  private String idToken;
}
