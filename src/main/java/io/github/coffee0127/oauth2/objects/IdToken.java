package io.github.coffee0127.oauth2.objects;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class IdToken {
  private final String issuer;
  private final String userId;
  private final String channelId;
  private final Instant expiryTime;
  private final Instant issuedTime;
  private final String nonce;
  private final String name;
  private final String picture;
}
