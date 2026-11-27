package com.corporatechat.nio;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public final class M4_FrameCodec {

    public static final int MAX_FRAME_SIZE = 1024 * 1024;
    private static final int HEADER_SIZE = Integer.BYTES;

    private M4_FrameCodec() {
    }

    public static void writeFrame(OutputStream out, byte[] payload) throws IOException {
        if (out == null) {
            throw new IllegalArgumentException("Output stream must not be null");
        }
        if (payload == null) {
            throw new IllegalArgumentException("Payload must not be null");
        }
        if (payload.length > MAX_FRAME_SIZE) {
            throw new IOException("Frame exceeds the 1 MB limit");
        }

        byte[] header = ByteBuffer.allocate(HEADER_SIZE).putInt(payload.length).array();
        out.write(header);
        out.write(payload);
        out.flush();
    }

    public static byte[] readFrame(InputStream in) throws IOException {
        if (in == null) {
            throw new IllegalArgumentException("Input stream must not be null");
        }

        ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE);
        if (!readFully(in, header, true, "Truncated frame header")) {
            return null;
        }

        header.flip();
        int length = header.getInt();
        if (length < 0 || length > MAX_FRAME_SIZE) {
            throw new IOException("Invalid frame length " + length + "; allowed range is 0 to "
                    + MAX_FRAME_SIZE + " bytes");
        }

        ByteBuffer payload = ByteBuffer.allocate(length);
        readFully(in, payload, false, "Truncated frame payload");
        return payload.array();
    }

    // Returns false only for clean EOF before any header bytes have been read.
    private static boolean readFully(InputStream in, ByteBuffer buffer, boolean allowCleanEnd, String eofMessage)
            throws IOException {
        while (buffer.hasRemaining()) {
            int count = in.read(buffer.array(), buffer.position(), buffer.remaining());
            if (count < 0) {
                if (allowCleanEnd && buffer.position() == 0) {
                    return false;
                }
                throw new EOFException(eofMessage);
            }
            if (count == 0) {
                int nextByte = in.read();
                if (nextByte < 0) {
                    if (allowCleanEnd && buffer.position() == 0) {
                        return false;
                    }
                    throw new EOFException(eofMessage);
                }
                buffer.put((byte) nextByte);
            } else {
                buffer.position(buffer.position() + count);
            }
        }
        return true;
    }
}