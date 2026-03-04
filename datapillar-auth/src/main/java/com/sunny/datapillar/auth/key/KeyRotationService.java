package com.sunny.datapillar.auth.key;

import org.springframework.stereotype.Component;

/** Key rotation coordinator. */
@Component
public class KeyRotationService {

  public void rotate() {
    throw new UnsupportedOperationException("manual key rotation is not implemented");
  }
}
