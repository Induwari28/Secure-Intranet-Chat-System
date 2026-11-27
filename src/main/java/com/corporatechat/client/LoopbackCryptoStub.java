package com.corporatechat.client;

import java.security.GeneralSecurityException;
import java.util.Objects;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Identity transform for local UI testing only. This provides no encryption and must not be used over a network.
 */
public final class LoopbackCryptoStub implements ClientCryptoWrapper {

    @Override
    public SecretKey generateSessionKey() {
        return new SecretKeySpec(new byte[16], "AES");
    }

    @Override
    public byte[] encrypt(byte[] plaintext, SecretKey key, long seqNum) throws GeneralSecurityException {
        Objects.requireNonNull(key, "key");
        return Objects.requireNonNull(plaintext, "plaintext").clone();
    }

    @Override
    public byte[] decrypt(byte[] ciphertext, SecretKey key, long expectedSeqNum) throws GeneralSecurityException {
        Objects.requireNonNull(key, "key");
        return Objects.requireNonNull(ciphertext, "ciphertext").clone();
    }
}
