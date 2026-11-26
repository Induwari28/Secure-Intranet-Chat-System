package com.corporatechat.security;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;

public class M1_SecurityManager {

    private static final String PROTOCOL = "TLSv1.3";
    private static final String[] ENABLED_PROTOCOLS = new String[]{PROTOCOL};

    private final int serverPort;
    private final SSLServerSocketFactory serverSocketFactory;

    public M1_SecurityManager(int serverPort) throws GeneralSecurityException, IOException {
        this.serverPort = serverPort;
        this.serverSocketFactory = createServerSocketFactory();
    }

    private SSLServerSocketFactory createServerSocketFactory() throws GeneralSecurityException, IOException {
        Path keyStorePath = TLSConfig.getKeystorePath();
        char[] keyStorePass = TLSConfig.getKeystorePassword();
        String keyStoreType = TLSConfig.getKeystoreType();

        KeyStore keyStore = KeyStore.getInstance(keyStoreType);
        try (InputStream in = Files.newInputStream(keyStorePath)) {
            keyStore.load(in, keyStorePass);
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keyStorePass);

        SSLContext context = SSLContext.getInstance(PROTOCOL);
        context.init(kmf.getKeyManagers(), null, null);
        return context.getServerSocketFactory();
    }

    private String[] strongCipherSuites(String[] enabledSuites) {
        return Arrays.stream(enabledSuites)
                .filter(suite -> !suite.contains("_RC4_")
                        && !suite.contains("_DES_")
                        && !suite.contains("_3DES_")
                        && !suite.contains("_NULL_")
                        && !suite.contains("_anon_")
                        && !suite.contains("EXPORT")
                        && !suite.contains("MD5")
                        && !suite.contains("SHA1"))
                .toArray(String[]::new);
    }

    public SSLServerSocket getServerSocket() throws IOException {
        SSLServerSocket serverSocket = (SSLServerSocket) serverSocketFactory.createServerSocket(serverPort);
        serverSocket.setReuseAddress(true);
        serverSocket.setEnabledProtocols(ENABLED_PROTOCOLS);

        String[] filteredCiphers = strongCipherSuites(serverSocket.getEnabledCipherSuites());
        if (filteredCiphers.length > 0) {
            serverSocket.setEnabledCipherSuites(filteredCiphers);
        }

        SSLParameters params = serverSocket.getSSLParameters();
        params.setProtocols(ENABLED_PROTOCOLS);
        serverSocket.setSSLParameters(params);

        return serverSocket;
    }

    public SSLSocket createClientSocket(String host, int port, String truststorePath)
            throws GeneralSecurityException, IOException {
        Path resolvedTruststore = (truststorePath != null && !truststorePath.isBlank())
                ? Paths.get(truststorePath)
                : TLSConfig.getDefaultTruststorePath();

        char[] trustStorePass = TLSConfig.getTruststorePassword();
        String trustStoreType = TLSConfig.getTruststoreType();

        KeyStore trustStore = KeyStore.getInstance(trustStoreType);
        try (InputStream in = Files.newInputStream(resolvedTruststore)) {
            trustStore.load(in, trustStorePass);
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        SSLContext clientContext = SSLContext.getInstance(PROTOCOL);
        clientContext.init(null, tmf.getTrustManagers(), null);

        SSLSocketFactory factory = clientContext.getSocketFactory();
        SSLSocket socket = (SSLSocket) factory.createSocket(host, port);
        socket.setEnabledProtocols(ENABLED_PROTOCOLS);

        String[] filteredCiphers = strongCipherSuites(socket.getEnabledCipherSuites());
        if (filteredCiphers.length > 0) {
            socket.setEnabledCipherSuites(filteredCiphers);
        }

        SSLParameters sslParams = socket.getSSLParameters();
        sslParams.setProtocols(ENABLED_PROTOCOLS);
        sslParams.setEndpointIdentificationAlgorithm("HTTPS");
        socket.setSSLParameters(sslParams);

        socket.startHandshake();
        return socket;
    }
}