package net.client.replay.record;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * One sound as it was actually played on the recording client.
 *
 * <p>{@code soundPath} keeps the resolved sound file (resource pack variants
 * stay faithful for the offline audio mix). {@code eventPath} keeps the sound
 * event id the game used, which is what {@code SoundManager#get} can resolve,
 * so a live replay can play the sound back in game.
 */
public final class SoundEventRecord {
    /** File signature at the head of {@code soundevents.bin} ("SONG").
     *  It only proves "this table was written by this client"; it carries no version. */
    public static final int MAGIC = 0x534F4E47;

    public long timeNanos;
    /** Sound event id (e.g. {@code game.player.hurt}). */
    public String eventPath;
    /** Resolved sound file (e.g. {@code minecraft:sounds/game/player/hurt.ogg}). */
    public String soundPath;
    public float x;
    public float y;
    public float z;
    public float volume;
    public float pitch;
    public boolean attenuated;
    public boolean repeatable;

    public void writeTable(DataOutput out) throws IOException {
        out.writeLong(timeNanos);
        out.writeUTF(eventPath == null ? "" : eventPath);
        out.writeUTF(soundPath == null ? "" : soundPath);
        out.writeFloat(x);
        out.writeFloat(y);
        out.writeFloat(z);
        out.writeFloat(volume);
        out.writeFloat(pitch);
        out.writeBoolean(attenuated);
        out.writeBoolean(repeatable);
    }

    public static SoundEventRecord readTable(DataInput in) throws IOException {
        SoundEventRecord e = new SoundEventRecord();
        e.timeNanos = in.readLong();
        e.eventPath = in.readUTF();
        e.soundPath = in.readUTF();
        e.x = in.readFloat();
        e.y = in.readFloat();
        e.z = in.readFloat();
        e.volume = in.readFloat();
        e.pitch = in.readFloat();
        e.attenuated = in.readBoolean();
        e.repeatable = in.readBoolean();
        return e;
    }

}
