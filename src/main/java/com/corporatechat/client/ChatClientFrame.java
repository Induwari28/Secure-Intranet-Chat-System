package com.corporatechat.client;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.SecretKey;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import com.corporatechat.model.MessageDto;
import com.corporatechat.model.MessageDto.MessageType;
import com.corporatechat.nio.M4_FrameCodec;

public class ChatClientFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private final JTextField usernameField = new JTextField("user", 12);
    private final JTextField recipientField = new JTextField(12);
    private final JTextArea messageArea = new JTextArea();
    private final JTextField messageInput = new JTextField();
    private final JButton sendButton = new JButton("Send");
    private final JLabel statusLabel = new JLabel("Not connected");
    private final Object outputLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "chat-client-send");
        thread.setDaemon(true);
        return thread;
    });

    private volatile InputStream input;
    private volatile OutputStream output;
    private volatile ClientCryptoWrapper crypto;
    private volatile SecretKey sessionKey;
    private volatile boolean connected;
    private boolean transportAttached;
    private long sendSeq;
    private volatile long recvSeq;

    public ChatClientFrame() {
        super("Corporate Chat");
        buildUi();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                disconnect("Disconnected");
            }
        });
    }

    public ChatClientFrame(String username, InputStream input, OutputStream output,
            SecretKey sessionKey, ClientCryptoWrapper crypto) {
        this();
        usernameField.setText(username == null ? "" : username);
        attachTransport(input, output, sessionKey, crypto);
    }

    private void buildUi() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        JPanel connectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        connectionPanel.add(new JLabel("Username:"));
        connectionPanel.add(usernameField);
        connectionPanel.add(new JLabel("Recipient (blank/ALL = broadcast):"));
        connectionPanel.add(recipientField);
        connectionPanel.add(statusLabel);
        add(connectionPanel, BorderLayout.NORTH);

        messageArea.setEditable(false);
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        add(new JScrollPane(messageArea), BorderLayout.CENTER);

        JPanel composePanel = new JPanel(new BorderLayout(6, 0));
        composePanel.add(messageInput, BorderLayout.CENTER);
        composePanel.add(sendButton, BorderLayout.EAST);
        add(composePanel, BorderLayout.SOUTH);

        sendButton.setEnabled(false);
        sendButton.addActionListener(event -> sendCurrentMessage());
        messageInput.addActionListener(event -> sendCurrentMessage());

        setSize(820, 540);
        setMinimumSize(new java.awt.Dimension(560, 360));
        setLocationByPlatform(true);
    }

    public final void attachTransport(InputStream input, OutputStream output,
            SecretKey sessionKey, ClientCryptoWrapper crypto) {
        if (input == null || output == null || sessionKey == null || crypto == null) {
            throw new IllegalArgumentException("Streams, session key, and crypto wrapper must not be null");
        }

        synchronized (outputLock) {
            if (transportAttached || closed.get()) {
                throw new IllegalStateException("A transport is already attached or the client is closed");
            }
            this.input = input;
            this.output = output;
            this.sessionKey = sessionKey;
            this.crypto = crypto;
            transportAttached = true;
            connected = true;
        }

        SwingUtilities.invokeLater(() -> {
            sendButton.setEnabled(true);
            statusLabel.setText("Connected");
        });

        Thread receiver = new Thread(this::receiveLoop, "chat-client-receive");
        receiver.setDaemon(true);
        receiver.start();
    }

    private void sendCurrentMessage() {
        if (!connected) {
            setStatus("Not connected");
            return;
        }

        String sender = usernameField.getText().trim();
        String content = messageInput.getText();
        String target = recipientField.getText().trim();
        if (sender.isEmpty()) {
            setStatus("Enter a username");
            return;
        }
        if (content.isBlank()) {
            return;
        }

        boolean broadcast = target.isEmpty() || MessageDto.BROADCAST.equalsIgnoreCase(target);
        MessageDto message = new MessageDto(
                broadcast ? MessageType.CHAT_BROADCAST : MessageType.CHAT_DIRECT,
                sender,
                broadcast ? null : target,
                content);
        try {
            sendExecutor.execute(() -> transmit(message, content));
        } catch (RejectedExecutionException exception) {
            setStatus("Send unavailable: " + exception.getMessage());
        }
    }

    private void transmit(MessageDto message, String submittedText) {
        try {
            synchronized (outputLock) {
                if (!connected || closed.get()) {
                    throw new IOException("Connection is closed");
                }
                long sequence = sendSeq;
                byte[] encrypted = crypto.encrypt(message.toJsonBytes(), sessionKey, sequence);
                M4_FrameCodec.writeFrame(output, encrypted);
                sendSeq = sequence + 1;
            }

            SwingUtilities.invokeLater(() -> {
                if (submittedText.equals(messageInput.getText())) {
                    messageInput.setText("");
                }
            });
        } catch (GeneralSecurityException | IOException | IllegalArgumentException exception) {
            disconnect("Send failed: " + exception.getMessage());
        }
    }

    private void receiveLoop() {
        try {
            while (!closed.get()) {
                byte[] encrypted = M4_FrameCodec.readFrame(input);
                if (encrypted == null) {
                    disconnect("Disconnected by server");
                    return;
                }

                long sequence = recvSeq;
                byte[] json = crypto.decrypt(encrypted, sessionKey, sequence);
                MessageDto message = MessageDto.fromJsonBytes(json);
                recvSeq = sequence + 1;
                String formatted = formatMessage(message);
                SwingUtilities.invokeLater(() -> appendMessage(formatted));
            }
        } catch (EOFException exception) {
            disconnect("Connection ended during a frame: " + exception.getMessage());
        } catch (IOException exception) {
            disconnect("Connection error: " + exception.getMessage());
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            disconnect("Could not decode server message: " + exception.getMessage());
        }
    }

    private String formatMessage(MessageDto message) {
        String sender = message.getSender() == null ? "unknown" : message.getSender();
        String recipient = message.isBroadcast()
                ? MessageDto.BROADCAST
                : message.getRecipient();
        String content = message.getType() == MessageType.LOGIN_REQUEST
                || message.getType() == MessageType.SESSION_KEY
                ? "[hidden]"
                : message.getContent() == null ? "" : message.getContent();

        return "[" + Instant.ofEpochMilli(message.getTimestamp()) + "] "
                + sender + " -> " + recipient + ": " + content;
    }

    private void appendMessage(String message) {
        if (messageArea.getDocument().getLength() > 0) {
            messageArea.append(System.lineSeparator());
        }
        messageArea.append(message);
        messageArea.setCaretPosition(messageArea.getDocument().getLength());
    }

    private void setStatus(String status) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(status));
    }

    void setStatusText(String status) {
        setStatus(status);
    }

    void reportLoopbackFailure(IOException exception) {
        disconnect("Loopback error: " + exception.getMessage());
    }

    private void disconnect(String status) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        connected = false;
        sendExecutor.shutdownNow();

        String finalStatus = status;
        finalStatus = closeResource(input, "input", finalStatus);
        finalStatus = closeResource(output, "output", finalStatus);
        String displayStatus = finalStatus;
        SwingUtilities.invokeLater(() -> {
            sendButton.setEnabled(false);
            statusLabel.setText(displayStatus);
        });
    }

    private String closeResource(Closeable resource, String name, String status) {
        if (resource == null) {
            return status;
        }
        try {
            resource.close();
            return status;
        } catch (IOException exception) {
            return status + " (failed to close " + name + ": " + exception.getMessage() + ")";
        }
    }
}
