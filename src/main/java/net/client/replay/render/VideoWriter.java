package net.client.replay.render;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface VideoWriter extends AutoCloseable {
    void writeFrame(ByteBuffer rgba, int width, int height) throws IOException;

    @Override
    void close() throws IOException;

    String describe();
}
