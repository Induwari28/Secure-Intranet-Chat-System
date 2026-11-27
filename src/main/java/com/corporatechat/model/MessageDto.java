package com.corporatechat.model;

import java.nio.charset.StandardCharsets;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * Shared wire message used by client, server and tests.
 * Pipeline: MessageDto -> JSON bytes -> encrypt -> writeFrame -> socket
 */
public class MessageDto {

    public enum MessageType {
        LOGIN_REQUEST,
        LOGIN_RESPONSE,
        SESSION_KEY,
        CHAT_BROADCAST,
        CHAT_DIRECT,
        HISTORY_SEARCH,
        HISTORY_RESPONSE,
        ACK,
        ERROR
    }

    public static final String BROADCAST = "ALL";

    private static final Gson GSON = new Gson();

    private MessageType type;
    private String sender;
    private String recipient;   // null or "ALL" = broadcast
    private String content;     // message body, or Base64 session key / payload
    private long timestamp;     // epoch millis

    public MessageDto() {
        this.timestamp = System.currentTimeMillis();
    }

    public MessageDto(MessageType type, String sender, String recipient, String content) {
        this.type = type;
        this.sender = sender;
        this.recipient = recipient;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    public MessageDto(MessageType type, String sender, String recipient, String content, long timestamp) {
        this(type, sender, recipient, content);
        this.timestamp = timestamp;
    }

    // ---------- JSON <-> UTF-8 bytes ----------

    public byte[] toJsonBytes() {
        return GSON.toJson(this).getBytes(StandardCharsets.UTF_8);
    }

    public static MessageDto fromJsonBytes(byte[] jsonBytes) {
        if (jsonBytes == null || jsonBytes.length == 0) {
            throw new IllegalArgumentException("Empty message payload");
        }
        try {
            MessageDto dto = GSON.fromJson(new String(jsonBytes, StandardCharsets.UTF_8), MessageDto.class);
            if (dto == null || dto.type == null) {
                throw new IllegalArgumentException("Message has no valid type");
            }
            return dto;
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Malformed message JSON", e);
        }
    }

    // ---------- helpers ----------

    public boolean isBroadcast() {
        return recipient == null || BROADCAST.equals(recipient);
    }

    // ---------- getters / setters ----------

    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        // never print passwords or session keys
        boolean secret = type == MessageType.LOGIN_REQUEST || type == MessageType.SESSION_KEY;
        return "MessageDto{type=" + type
                + ", sender=" + sender
                + ", recipient=" + (isBroadcast() ? BROADCAST : recipient)
                + ", content=" + (secret ? "[HIDDEN]" : content)
                + ", timestamp=" + timestamp + "}";
    }
}