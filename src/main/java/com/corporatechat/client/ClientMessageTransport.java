package com.corporatechat.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.SecretKey;

import com.corporatechat.model.MessageDto;
import com.corporatechat.nio.M4_FrameCodec;

public final class ClientMessageTransport implements AutoCloseable {

    private final InputStream input;
    private final OutputStream output;
    private final SecretKey sessionKey;
    private final ClientCryptoWrapper crypto;
    private final Object sendLock = new Object();
    private final Object receiveLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean();

    private long sendSeq;
    private long recvSeq;

    public ClientMessageTransport(InputStream input, OutputStream output,
            SecretKey sessionKey, ClientCryptoWrapper crypto) {
        if (input == null || output == null || sessionKey == null || crypto == null) {
            throw new IllegalArgumentException("Streams, session key, and crypto wrapper must not be null");
        }
        this.input = input;
        this.output = output;
        this.sessionKey = sessionKey;
        this.crypto = crypto;
    }

    public void send(MessageDto message) throws IOException, GeneralSecurityException {
        if (message == null) {
            throw new IllegalArgumentException("Message must not be null");
        }

        synchronized (sendLock) {
            ensureOpen();
            long sequence = sendSeq;
            byte[] encrypted = crypto.encrypt(message.toJsonBytes(), sessionKey, sequence);
            M4_FrameCodec.writeFrame(output, encrypted);
            sendSeq = sequence + 1;
        }
    }

    public MessageDto receive() throws IOException, GeneralSecurityException {
        synchronized (receiveLock) {
            ensureOpen();
            byte[] encrypted = M4_FrameCodec.readFrame(input);
            if (encrypted == null) {
                return null;
            }

            long sequence = recvSeq;
            byte[] json = crypto.decrypt(encrypted, sessionKey, sequence);
            MessageDto message = MessageDto.fromJsonBytes(json);
            recvSeq = sequence + 1;
            return message;
        }
    }

    public long getSendSeq() {
        synchronized (sendLock) {
            return sendSeq;
        }
    }

    public long getRecvSeq() {
        synchronized (receiveLock) {
            return recvSeq;
        }
    }

    private void ensureOpen() throws IOException {
        if (closed.get()) {
            throw new IOException("Client transport is closed");
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        IOException failure = null;
        try {
            input.close();
        } catch (IOException exception) {
            failure = exception;
        }
        try {
            output.close();
        } catch (IOException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
