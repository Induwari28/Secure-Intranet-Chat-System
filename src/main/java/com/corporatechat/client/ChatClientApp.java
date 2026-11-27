package com.corporatechat.client;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Arrays;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.corporatechat.nio.M4_FrameCodec;

public final class ChatClientApp {

    private ChatClientApp() {
    }

    public static void main(String[] args) {
        boolean disconnected = Arrays.asList(args).contains("--disconnected");
        SwingUtilities.invokeLater(() -> {
            try {
                ChatClientFrame frame = disconnected
                        ? new ChatClientFrame()
                        : createLoopbackFrame("local-user");
                frame.setVisible(true);
            } catch (IOException exception) {
                JOptionPane.showMessageDialog(null,
                        "Could not start the local chat loopback: " + exception.getMessage(),
                        "Chat Client Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    public static ChatClientFrame createLoopbackFrame(String username) throws IOException {
        PipedInputStream clientInput = new PipedInputStream(64 * 1024);
        PipedOutputStream serverOutput = new PipedOutputStream(clientInput);
        PipedInputStream serverInput = new PipedInputStream(64 * 1024);
        PipedOutputStream clientOutput = new PipedOutputStream(serverInput);

        LoopbackCryptoStub crypto = new LoopbackCryptoStub();
        ChatClientFrame frame = new ChatClientFrame(
                username, clientInput, clientOutput, crypto.generateSessionKey(), crypto);
        frame.setStatusText("Connected - local loopback (identity crypto stub; not secure)");

        Thread echoServer = new Thread(() -> runEchoServer(serverInput, serverOutput, frame),
                "chat-client-loopback");
        echoServer.setDaemon(true);
        echoServer.start();
        return frame;
    }

    private static void runEchoServer(PipedInputStream input, PipedOutputStream output, ChatClientFrame frame) {
        try (PipedInputStream serverInput = input; PipedOutputStream serverOutput = output) {
            byte[] frameBytes;
            while ((frameBytes = M4_FrameCodec.readFrame(serverInput)) != null) {
                M4_FrameCodec.writeFrame(serverOutput, frameBytes);
            }
        } catch (IOException exception) {
            frame.reportLoopbackFailure(exception);
        }
    }
}