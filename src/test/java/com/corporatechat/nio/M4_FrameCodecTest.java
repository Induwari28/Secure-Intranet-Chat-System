package com.corporatechat.nio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

class M4_FrameCodecTest {

    @Test
    void writesAndReadsFrameAndReturnsNullAtCleanEnd() throws IOException {
        byte[] payload = {1, 2, 3};
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        M4_FrameCodec.writeFrame(out, payload);
        assertEquals(payload.length + Integer.BYTES, out.size());

        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        assertArrayEquals(payload, M4_FrameCodec.readFrame(in));
        assertNull(M4_FrameCodec.readFrame(in));
    }

    @Test
    void readsEmptyFrame() throws IOException {
        byte[] frame = ByteBuffer.allocate(Integer.BYTES).putInt(0).array();

        assertArrayEquals(new byte[0], M4_FrameCodec.readFrame(new ByteArrayInputStream(frame)));
    }

    @Test
    void rejectsTruncatedHeaderAndPayload() {
        assertThrows(EOFException.class,
                () -> M4_FrameCodec.readFrame(new ByteArrayInputStream(new byte[] {0, 0})));

        byte[] truncatedPayload = ByteBuffer.allocate(Integer.BYTES + 1).putInt(2).put((byte) 1).array();
        assertThrows(EOFException.class,
                () -> M4_FrameCodec.readFrame(new ByteArrayInputStream(truncatedPayload)));
    }

    @Test
    void rejectsInvalidFrameLengths() {
        byte[] negativeLength = ByteBuffer.allocate(Integer.BYTES).putInt(-1).array();
        byte[] oversizedLength = ByteBuffer.allocate(Integer.BYTES)
                .putInt(M4_FrameCodec.MAX_FRAME_SIZE + 1).array();

        assertThrows(IOException.class,
                () -> M4_FrameCodec.readFrame(new ByteArrayInputStream(negativeLength)));
        assertThrows(IOException.class,
                () -> M4_FrameCodec.readFrame(new ByteArrayInputStream(oversizedLength)));
    }

    @Test
    void rejectsNullArgumentsAndOversizedPayload() {
        assertThrows(IllegalArgumentException.class, () -> M4_FrameCodec.writeFrame(null, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> M4_FrameCodec.writeFrame(new ByteArrayOutputStream(), null));
        assertThrows(IllegalArgumentException.class, () -> M4_FrameCodec.readFrame(null));
        assertThrows(IOException.class,
                () -> M4_FrameCodec.writeFrame(new ByteArrayOutputStream(),
                        new byte[M4_FrameCodec.MAX_FRAME_SIZE + 1]));
    }

    @Test
    void acceptsMaximumSizedFrame() throws IOException {
        byte[] payload = new byte[M4_FrameCodec.MAX_FRAME_SIZE];
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        M4_FrameCodec.writeFrame(out, payload);

        assertArrayEquals(payload, M4_FrameCodec.readFrame(new ByteArrayInputStream(out.toByteArray())));
    }
}