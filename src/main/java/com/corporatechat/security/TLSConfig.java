public class TLSConfig {
    
}
package com.corporatechat.security;

import java.nio.file.Path;
import java.nio.file.Paths;

public class TLSConfig {

    private static final String DEFAULT_KEYSTORE_PATH = "src/main/resources/certificates/server-keystore.p12";
    private static final String DEFAULT_TRUSTSTORE_PATH = "src/main/resources/certificates/client-truststore.p12";
    private static final String DEFAULT_STORE_TYPE = "PKCS12";
    private static final String DEFAULT_STORE_PASS = "changeit";

    private TLSConfig() {
    }

    private static String resolveValue(String envKey, String propKey, String fallback) {
        String envVal = System.getenv(envKey);
        if (envVal != null && !envVal.isBlank()) {
            return envVal.trim();
        }
        String propVal = System.getProperty(propKey);
        if (propVal != null && !propVal.isBlank()) {
            return propVal.trim();
        }
        return fallback;
    }

    public static Path getKeystorePath() {
        return Paths.get(resolveValue("TLS_KEYSTORE_PATH", "tls.keystore.path", DEFAULT_KEYSTORE_PATH));
    }

    public static char[] getKeystorePassword() {
        return resolveValue("TLS_KEYSTORE_PASSWORD", "tls.keystore.password", DEFAULT_STORE_PASS).toCharArray();
    }

    public static String getKeystoreType() {
        return resolveValue("TLS_KEYSTORE_TYPE", "tls.keystore.type", DEFAULT_STORE_TYPE);
    }

    public static Path getDefaultTruststorePath() {
        return Paths.get(resolveValue("TLS_TRUSTSTORE_PATH", "tls.truststore.path", DEFAULT_TRUSTSTORE_PATH));
    }

    public static char[] getTruststorePassword() {
        return resolveValue("TLS_TRUSTSTORE_PASSWORD", "tls.truststore.password", DEFAULT_STORE_PASS).toCharArray();
    }

    public static String getTruststoreType() {
        return resolveValue("TLS_TRUSTSTORE_TYPE", "tls.truststore.type", DEFAULT_STORE_TYPE);
    }
}