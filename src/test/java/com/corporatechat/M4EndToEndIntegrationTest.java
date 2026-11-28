package com.corporatechat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import com.corporatechat.client.ClientCryptoWrapper;
import com.corporatechat.client.ClientMessageTransport;
import com.corporatechat.model.MessageDto;
import com.corporatechat.model.MessageDto.MessageType;
import com.corporatechat.nio.M4_FrameCodec;

class M4EndToEndIntegrationTest {

    private static final char[] STORE_PASSWORD = "test-password".toCharArray();

    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void twoClientsExchangeSequencedMessagesThroughFramedHub() throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        try (ServerSocket listener = new ServerSocket(0, 2, loopback)) {
            Socket socketA = new Socket();
            socketA.connect(new InetSocketAddress(loopback, listener.getLocalPort()), 2000);
            Socket hubSocketA = listener.accept();
            Socket socketB = new Socket();
            socketB.connect(new InetSocketAddress(loopback, listener.getLocalPort()), 2000);
            Socket hubSocketB = listener.accept();
            socketA.setSoTimeout(5000);
            socketB.setSoTimeout(5000);
            hubSocketA.setSoTimeout(5000);
            hubSocketB.setSoTimeout(5000);

            RecordingCrypto cryptoA = new RecordingCrypto();
            RecordingCrypto cryptoB = new RecordingCrypto();
            ExecutorService hub = Executors.newFixedThreadPool(2, task -> {
                Thread thread = new Thread(task, "test-message-hub");
                thread.setDaemon(true);
                return thread;
            });
            try (Socket clientSocketA = socketA;
                    Socket clientSocketB = socketB;
                    Socket serverSocketA = hubSocketA;
                    Socket serverSocketB = hubSocketB;
                    ClientMessageTransport clientA = new ClientMessageTransport(
                            clientSocketA.getInputStream(), clientSocketA.getOutputStream(),
                            TEST_KEY, cryptoA);
                    ClientMessageTransport clientB = new ClientMessageTransport(
                            clientSocketB.getInputStream(), clientSocketB.getOutputStream(),
                            TEST_KEY, cryptoB)) {
                hub.submit(() -> {
                    relay(serverSocketA, serverSocketB);
                    return null;
                });
                hub.submit(() -> {
                    relay(serverSocketB, serverSocketA);
                    return null;
                });

                MessageDto direct = new MessageDto(MessageType.CHAT_DIRECT, "alice", "bob", "hello bob");
                clientA.send(direct);
                MessageDto receivedByB = clientB.receive();
                assertEquals(MessageType.CHAT_DIRECT, receivedByB.getType());
                assertEquals("alice", receivedByB.getSender());
                assertEquals("bob", receivedByB.getRecipient());
                assertEquals("hello bob", receivedByB.getContent());

                assertEquals(1, clientA.getSendSeq());
                assertEquals(0, clientA.getRecvSeq());
                assertEquals(0, clientB.getSendSeq());
                assertEquals(1, clientB.getRecvSeq());

                MessageDto broadcast = new MessageDto(
                        MessageType.CHAT_BROADCAST, "bob", null, "hello everyone");
                clientB.send(broadcast);
                MessageDto receivedByA = clientA.receive();
                assertEquals(MessageType.CHAT_BROADCAST, receivedByA.getType());
                assertEquals("bob", receivedByA.getSender());
                assertEquals("hello everyone", receivedByA.getContent());
                assertEquals(1, clientA.getRecvSeq());
                assertEquals(1, clientB.getSendSeq());

                assertEquals(List.of(0L), cryptoA.encryptedSequences());
                assertEquals(List.of(0L), cryptoA.decryptedSequences());
                assertEquals(List.of(0L), cryptoB.encryptedSequences());
                assertEquals(List.of(0L), cryptoB.decryptedSequences());
            } finally {
                hub.shutdownNow();
            }
        }
    }

    private static final SecretKey TEST_KEY = new SecretKeySpec(new byte[16], "AES");

    private static void relay(Socket source, Socket destination) throws IOException {
        byte[] frame;
        while ((frame = M4_FrameCodec.readFrame(source.getInputStream())) != null) {
            M4_FrameCodec.writeFrame(destination.getOutputStream(), frame);
        }
    }

    @Test
    void frameCodecHandlesSingleByteAndSmallChunkReads() throws IOException {
        byte[] expected = new MessageDto(
                MessageType.CHAT_DIRECT, "alice", "bob", "chunked payload").toJsonBytes();
        byte[] frame = encodeFrame(expected);

        assertArrayEquals(expected, M4_FrameCodec.readFrame(new ChunkedInputStream(frame, 1)));
        assertArrayEquals(expected, M4_FrameCodec.readFrame(new ChunkedInputStream(frame, 3)));
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void malformedFrameLengthsAndTruncationFailPromptly() {
        byte[] oversizedLength = ByteBuffer.allocate(Integer.BYTES)
                .putInt(M4_FrameCodec.MAX_FRAME_SIZE + 1).array();
        IOException oversizedFrameException = assertThrows(IOException.class,
            () -> M4_FrameCodec.readFrame(new ByteArrayInputStream(oversizedLength)));
        assertNotNull(oversizedFrameException.getMessage());

        EOFException truncatedHeaderException = assertThrows(EOFException.class,
                () -> M4_FrameCodec.readFrame(new ByteArrayInputStream(new byte[] {0, 0})));
        assertNotNull(truncatedHeaderException);

        byte[] truncatedPayload = ByteBuffer.allocate(Integer.BYTES + 1)
                .putInt(2).put((byte) 1).array();
        EOFException truncatedPayloadException = assertThrows(EOFException.class,
                () -> M4_FrameCodec.readFrame(new ByteArrayInputStream(truncatedPayload)));
        assertNotNull(truncatedPayloadException);
    }

    @Test
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    void tlsClientRejectsUntrustedServerCertificate() throws Exception {
        Path serverKeyStorePath = temporaryDirectory.resolve("server-keystore.p12");
        generateServerKeyStore(serverKeyStorePath);

        KeyStore serverKeyStore = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(serverKeyStorePath)) {
            serverKeyStore.load(input, STORE_PASSWORD);
        }
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(serverKeyStore, STORE_PASSWORD);

        SSLContext serverContext = SSLContext.getInstance("TLSv1.3");
        serverContext.init(keyManagers.getKeyManagers(), null, null);

        Path clientTrustStorePath = createUnrelatedTrustStore();
        KeyStore clientTrustStore = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(clientTrustStorePath)) {
            clientTrustStore.load(input, STORE_PASSWORD);
        }
        TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(clientTrustStore);

        SSLContext clientContext = SSLContext.getInstance("TLSv1.3");
        clientContext.init(null, trustManagers.getTrustManagers(), null);

        try (SSLServerSocket server = (SSLServerSocket) serverContext
                .getServerSocketFactory().createServerSocket(0)) {
            server.setEnabledProtocols(new String[] {"TLSv1.3"});
            ExecutorService handshakeExecutor = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "test-tls-server");
                thread.setDaemon(true);
                return thread;
            });
            try {
                Future<?> serverHandshake = handshakeExecutor.submit(() -> {
                    try (SSLSocket peer = (SSLSocket) server.accept()) {
                        peer.setSoTimeout(5000);
                        try {
                            peer.startHandshake();
                        } catch (IOException expectedClientRejection) {
                            return null;
                        }
                    }
                    return null;
                });

                try (SSLSocket client = (SSLSocket) clientContext.getSocketFactory().createSocket()) {
                    client.connect(new InetSocketAddress(
                            InetAddress.getLoopbackAddress(), server.getLocalPort()), 2000);
                    client.setSoTimeout(5000);
                        SSLHandshakeException handshakeException = assertThrows(
                            SSLHandshakeException.class, client::startHandshake);
                        assertNotNull(handshakeException);
                }
                serverHandshake.get(6, TimeUnit.SECONDS);
            } finally {
                handshakeExecutor.shutdownNow();
            }
        }
    }

    private void generateServerKeyStore(Path keyStorePath) throws Exception {
        runKeytool(
                "-genkeypair",
                "-alias", "server",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "2",
                "-dname", "CN=localhost",
                "-ext", "SAN=DNS:localhost,IP:127.0.0.1",
                "-storetype", "PKCS12",
                "-keystore", keyStorePath.toString(),
                "-storepass", new String(STORE_PASSWORD),
                "-keypass", new String(STORE_PASSWORD),
                "-noprompt");
    }

    private Path createUnrelatedTrustStore() throws Exception {
        Path unrelatedKeyStore = temporaryDirectory.resolve("unrelated-keystore.p12");
        Path certificate = temporaryDirectory.resolve("unrelated.cer");
        Path trustStore = temporaryDirectory.resolve("client-truststore.p12");
        runKeytool(
                "-genkeypair",
                "-alias", "unrelated",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "2",
                "-dname", "CN=unrelated",
                "-storetype", "PKCS12",
                "-keystore", unrelatedKeyStore.toString(),
                "-storepass", new String(STORE_PASSWORD),
                "-keypass", new String(STORE_PASSWORD),
                "-noprompt");
        runKeytool(
                "-exportcert",
                "-alias", "unrelated",
                "-keystore", unrelatedKeyStore.toString(),
                "-storepass", new String(STORE_PASSWORD),
                "-file", certificate.toString());
        runKeytool(
                "-importcert",
                "-alias", "unrelated",
                "-file", certificate.toString(),
                "-storetype", "PKCS12",
                "-keystore", trustStore.toString(),
                "-storepass", new String(STORE_PASSWORD),
                "-noprompt");
        return trustStore;
    }

    private void runKeytool(String... arguments) throws Exception {
        String executable = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
                ? "keytool.exe"
                : "keytool";
        Path keytool = Path.of(System.getProperty("java.home"), "bin", executable);
        Path log = temporaryDirectory.resolve("keytool.log");

        List<String> command = new ArrayList<>();
        command.add(keytool.toString());
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();

        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Timed out generating the TLS test certificate");
        }
        if (process.exitValue() != 0) {
            throw new IOException("Could not generate the TLS test certificate: "
                    + Files.readString(log));
        }
    }

    private static byte[] encodeFrame(byte[] payload) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        M4_FrameCodec.writeFrame(output, payload);
        return output.toByteArray();
    }

    private static final class ChunkedInputStream extends ByteArrayInputStream {
        private final int maximumChunk;

        private ChunkedInputStream(byte[] bytes, int maximumChunk) {
            super(bytes);
            this.maximumChunk = maximumChunk;
        }

        @Override
        public synchronized int read(byte[] buffer, int offset, int length) {
            return super.read(buffer, offset, Math.min(length, maximumChunk));
        }
    }

    private static final class RecordingCrypto implements ClientCryptoWrapper {
        private final List<Long> encrypted = Collections.synchronizedList(new ArrayList<>());
        private final List<Long> decrypted = Collections.synchronizedList(new ArrayList<>());

        @Override
        public SecretKey generateSessionKey() {
            return TEST_KEY;
        }

        @Override
        public byte[] encrypt(byte[] plaintext, SecretKey key, long seqNum) {
            encrypted.add(seqNum);
            return plaintext.clone();
        }

        @Override
        public byte[] decrypt(byte[] ciphertext, SecretKey key, long expectedSeqNum) {
            decrypted.add(expectedSeqNum);
            return ciphertext.clone();
        }

        private List<Long> encryptedSequences() {
            synchronized (encrypted) {
                return List.copyOf(encrypted);
            }
        }

        private List<Long> decryptedSequences() {
            synchronized (decrypted) {
                return List.copyOf(decrypted);
            }
        }
    }
}
