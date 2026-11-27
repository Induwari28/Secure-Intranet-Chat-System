package com.corporatechat.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.corporatechat.model.MessageDto.MessageType;

class MessageDtoTest {

    @Test
    void jsonRoundTrip() {
        MessageDto in = new MessageDto(MessageType.CHAT_DIRECT, "alice", "bob", "hi \"there\"\nnew line");
        MessageDto out = MessageDto.fromJsonBytes(in.toJsonBytes());
        assertEquals(MessageType.CHAT_DIRECT, out.getType());
        assertEquals("alice", out.getSender());
        assertEquals("bob", out.getRecipient());
        assertEquals(in.getContent(), out.getContent());
        assertEquals(in.getTimestamp(), out.getTimestamp());
    }

    @Test
    void nullRecipientIsBroadcast() {
        MessageDto m = new MessageDto(MessageType.CHAT_BROADCAST, "alice", null, "hello all");
        assertTrue(m.isBroadcast());
    }

    @Test
    void malformedJsonRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> MessageDto.fromJsonBytes("{not json".getBytes()));
    }

    @Test
    void secretsHiddenInToString() {
        MessageDto m = new MessageDto(MessageType.SESSION_KEY, "server", "alice", "BASE64KEY==");
        assertFalse(m.toString().contains("BASE64KEY"));
    }
}