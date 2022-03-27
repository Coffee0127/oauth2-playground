package io.github.coffee0127.oauth2.constant;

import lombok.Getter;

@Getter
public enum ErrorCode {
  LOGIN_FAILED_CALLBACK(40100, "Login failed - Call back with error."),
  LOGIN_FAILED_MISMATCH_STATE(40101, "Login failed - Mismatch state."),
  LOGIN_FAILED_INVALID_ID_TOKEN(40102, "Login failed - id_token is invalid.");

  private final int code;
  private final String errorMessage;

  ErrorCode(int code, String errorMessage) {
    this.code = code;
    this.errorMessage = errorMessage;
  }
}
