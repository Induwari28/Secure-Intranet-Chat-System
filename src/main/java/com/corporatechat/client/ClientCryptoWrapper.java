package com.corporatechat.client;

import java.security.GeneralSecurityException;

import javax.crypto.SecretKey;

public interface ClientCryptoWrapper {

    SecretKey generateSessionKey() throws GeneralSecurityException;

    byte[] encrypt(byte[] plaintext, SecretKey key, long seqNum) throws GeneralSecurityException;

    byte[] decrypt(byte[] ciphertext, SecretKey key, long expectedSeqNum) throws GeneralSecurityException;
}
