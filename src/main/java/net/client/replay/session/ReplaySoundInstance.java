package net.client.replay.session;

import net.minecraft.client.sound.SoundInstance;
import net.minecraft.util.Identifier;

/**
 * A sound that came out of a replay's sound event log.
 *
 * <p>The recorded volume already contains the distance falloff that applied
 * while recording, so this instance uses {@link AttenuationType#NONE} — the
 * replay camera stands where the recorder stood, therefore attenuating a second
 * time would make everything quieter than it actually was.
 */
public final class ReplaySoundInstance implements SoundInstance {
    private final Identifier identifier;
    private final float volume;
    private final float pitch;
    private final float x;
    private final float y;
    private final float z;

    public ReplaySoundInstance(Identifier identifier, float volume, float pitch, float x, float y, float z) {
        this.identifier = identifier;
        this.volume = volume;
        this.pitch = pitch;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public Identifier getIdentifier() {
        return identifier;
    }

    @Override
    public boolean isRepeatable() {
        return false;
    }

    @Override
    public int getRepeatDelay() {
        return 0;
    }

    @Override
    public float getVolume() {
        return volume;
    }

    @Override
    public float getPitch() {
        return pitch;
    }

    @Override
    public float getX() {
        return x;
    }

    @Override
    public float getY() {
        return y;
    }

    @Override
    public float getZ() {
        return z;
    }

    @Override
    public AttenuationType getAttenuationType() {
        return AttenuationType.NONE;
    }
}
