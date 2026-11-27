package net.client.replay.session;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/** Optional cinematic camera track stored next to the replay as *.camera.json. */
public final class CameraPath {
    public static final class Keyframe {
        public double time;
        /** 0 = player view, 1 = free camera, 2 = target player (CameraController constants). */
        public int mode = 1; // legacy tracks were all free-camera
        public int targetEntityId = -1;
        public int perspective = 0;
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;
        public float fov = 70.0F;
    }

    public static final class Transform {
        public int mode = 1;
        public int targetEntityId = -1;
        public int perspective = 0;
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;
        public float fov = 70.0F;
    }

    public List<Keyframe> keyframes = new ArrayList<Keyframe>();

    /** 导出片段起止（秒）。-1 表示这份轨道文件没存过范围。 */
    public double trimStartSeconds = -1.0D;
    public double trimEndSeconds = -1.0D;

    /** 保存时播放头所在位置（秒）。-1 表示没记录，重新打开时从片段起点开始。 */
    public double editorSeconds = -1.0D;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static CameraPath load(File file) {
        if (file == null || !file.isFile()) return null;
        try (Reader reader = new FileReader(file)) {
            CameraPath path = GSON.fromJson(reader, CameraPath.class);
            if (path == null) return null;
            if (path.keyframes == null) path.keyframes = new ArrayList<Keyframe>();
            // 只保存了片段范围、没加关键帧的轨道也要能读回来，否则
            // 重新打开剪辑台时起点/终点会被重置成整段。
            if (path.keyframes.isEmpty() && path.trimStartSeconds < 0.0D
                    && path.editorSeconds < 0.0D) {
                return null;
            }
            path.sort();
            return path;
        } catch (Throwable t) {
            return null;
        }
    }

    private void sort() {
        java.util.Collections.sort(keyframes, new java.util.Comparator<Keyframe>() {
            @Override
            public int compare(Keyframe a, Keyframe b) {
                return Double.compare(a.time, b.time);
            }
        });
    }

    public void save(File file) {
        try {
            if (file.getParentFile() != null && !file.getParentFile().isDirectory()) file.getParentFile().mkdirs();
            java.io.Writer writer = new java.io.FileWriter(file);
            try {
                GSON.toJson(this, writer);
            } finally {
                writer.close();
            }
        } catch (Throwable t) {
            // Caller logs; a failed camera save must not break the editor.
        }
    }

    public void add(Keyframe frame) {
        keyframes.add(frame);
        sort();
    }

    public static final class Segment {
        public final Keyframe a;
        public final Keyframe b;
        public final double t;

        Segment(Keyframe a, Keyframe b, double t) {
            this.a = a;
            this.b = b;
            this.t = t;
        }
    }

    /** 返回当前时间落在哪两个关键帧之间；不在关键帧区间内返回 null。 */
    public Segment segmentAt(double seconds) {
        if (keyframes.size() < 2) return null;
        if (seconds <= keyframes.get(0).time) return null;
        if (seconds >= keyframes.get(keyframes.size() - 1).time) return null;
        for (int i = 0; i < keyframes.size() - 1; i++) {
            Keyframe a = keyframes.get(i);
            Keyframe b = keyframes.get(i + 1);
            if (seconds >= a.time && seconds < b.time) {
                double span = b.time - a.time;
                double t = span <= 0.0D ? 1.0D : (seconds - a.time) / span;
                return new Segment(a, b, t);
            }
        }
        return null;
    }

    public Transform at(double seconds) {
        if (keyframes.isEmpty()) return null;
        Keyframe first = keyframes.get(0);
        if (seconds <= first.time) return copy(first);
        Keyframe last = keyframes.get(keyframes.size() - 1);
        if (seconds >= last.time) return copy(last);
        for (int i = 0; i < keyframes.size() - 1; i++) {
            Keyframe a = keyframes.get(i);
            Keyframe b = keyframes.get(i + 1);
            if (seconds >= b.time) return copy(b);
            if (seconds >= a.time && seconds <= b.time) {
                // Camera type changes are cuts, not blends: hold A until B's
                // timestamp.  Free-camera keys interpolate normally.
                if (a.mode != b.mode || a.mode != 1 || a.targetEntityId != b.targetEntityId) {
                    return copy(a);
                }
                double span = b.time - a.time;
                float t = span <= 0.0D ? 1.0F : (float) ((seconds - a.time) / span);
                Transform transform = copy(a);
                transform.x = a.x + (b.x - a.x) * t;
                transform.y = a.y + (b.y - a.y) * t;
                transform.z = a.z + (b.z - a.z) * t;
                transform.yaw = lerpAngle(a.yaw, b.yaw, t);
                transform.pitch = a.pitch + (b.pitch - a.pitch) * t;
                transform.fov = a.fov + (b.fov - a.fov) * t;
                return transform;
            }
        }
        return copy(last);
    }

    private static Transform copy(Keyframe frame) {
        Transform transform = new Transform();
        transform.mode = frame.mode;
        transform.targetEntityId = frame.targetEntityId;
        transform.perspective = frame.perspective;
        transform.x = frame.x;
        transform.y = frame.y;
        transform.z = frame.z;
        transform.yaw = frame.yaw;
        transform.pitch = frame.pitch;
        transform.fov = frame.fov;
        return transform;
    }

    private static float lerpAngle(float a, float b, float t) {
        float difference = b - a;
        while (difference < -180.0F) difference += 360.0F;
        while (difference >= 180.0F) difference -= 360.0F;
        return a + difference * t;
    }
}
