package com.sunny.datapillar.auth.key;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;

/** Key manager for active Ed25519 signing key and metadata. */
public interface KeyManager {

  String activeKid();

  PublicKey publicKey();

  PrivateKey privateKey();

  List<Map<String, Object>> publicJwks();
}
