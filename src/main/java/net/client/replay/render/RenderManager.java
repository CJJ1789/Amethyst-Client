package net.client.replay.render;

import net.client.replay.ReplayClient;
import net.client.replay.gui.ReplayProgressScreen;
import net.client.replay.session.CameraController;
import net.client.replay.util.ReplayPaths;
import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import net.minecraft.client.MinecraftClient;

public final class RenderManager {
    private final net.minecraft.client.MinecraftClient client;
    private RenderJob job;
    private PreviewJob preview;

    public RenderManager(net.minecraft.client.MinecraftClient client) {
        this.client = client;
    }

    public boolean isActive() {
        return job != null || preview != null;
    }

    public boolean isRendering() {
        return job != null;
    }

    public boolean isBackgroundRendering() {
        return job != null && job.isBackground();
    }

    public boolean start(File replay, double fps, int width, int height, String encoder) {
        return start(replay, fps, width, height, encoder, -1L, -1L);
    }

    public boolean start(File replay, double fps, int width, int height, String encoder,
                         long trimStartNanos, long trimEndNanos) {
        return start(replay, fps, width, height, encoder, trimStartNanos, trimEndNanos, null);
    }

    public boolean start(File replay, double fps, int width, int height, String encoder,
                         long trimStartNanos, long trimEndNanos, CameraController sourceCamera) {
        if (isActive()) {
            ReplayClient.chat("已有回放任务在运行，请先执行 .replay stop。");
            return false;
        }
        if (client.world != null && preview != null) {
            ReplayClient.chat("A replay preview is already active. Use .replay stop first.");
            return false;
        }
        boolean background = client.world != null;
        if (client.world == null && preview != null) {
            ReplayClient.chat("A replay preview is already active. Use .replay stop first.");
            return false;
        }
        try {
            net.client.util.AfterimageHandler.clearAll();
            job = new RenderJob(client, replay, fps, width, height, encoder, trimStartNanos, trimEndNanos, sourceCamera, background);
            ReplayClient.chat(background
                    ? "后台渲染已开始：继续游玩/录制，输入 .replay stop 可停止。"
                    : "Render queued: " + replay.getName() + ", " + width + "x" + height
                    + ", " + fps + " fps, encoder " + encoder);
            if (!background) client.setScreen(new ReplayProgressScreen(job));
            return true;
        } catch (Throwable t) {
            ReplayClient.LOGGER.error("Could not start render", t);
            ReplayClient.chat("Could not start render: " + t.getMessage());
            job = null;
            return false;
        }
    }

    public boolean startLatest() {
        File file = latestReplay();
        if (file == null) {
            ReplayClient.chat("No .lcr replay found in " + ReplayPaths.replaysDir().getAbsolutePath());
            return false;
        }
        return start(file,
                ReplayClient.getConfig().defaultFps,
                ReplayClient.getConfig().defaultWidth,
                ReplayClient.getConfig().defaultHeight,
                ReplayClient.getConfig().encoder);
    }

    public RenderJob getJob() {
        return job;
    }

    public PreviewJob getPreview() {
        return preview;
    }

    public boolean startPreview(File replay) {
        if (isActive()) {
            ReplayClient.chat("已有回放任务在运行，请先执行 .replay stop。");
            return false;
        }
        if (client.world != null) {
            ReplayClient.chat("编辑/渲染只能在主菜单进行；游戏中只能录制。");
            return false;
        }
        try {
            net.client.util.AfterimageHandler.clearAll();
            preview = new PreviewJob(client, replay);
            return true;
        } catch (Throwable t) {
            ReplayClient.LOGGER.error("Could not start preview", t);
            ReplayClient.chat("Could not start preview: " + t.getMessage());
            preview = null;
            return false;
        }
    }

    /** Called at the tail of every client frame. */
    public void renderTick() {
        if (job != null) {
            job.tick(ReplayClient.getConfig().renderBudgetMs);
        }
    }

    /** Called at the tail of every client tick. */
    public void clientTick() {
        if (preview != null) {
            preview.tick();
        }
    }

    public void stop() {
        if (job != null) {
            job.cancel();
            job = null;
            return;
        }
        if (preview != null) {
            preview.stop();
            preview = null;
            return;
        }
        ReplayClient.chat("No replay job is active.");
    }

    public void onJobFinished(RenderJob finishedJob) {
        if (job == finishedJob) job = null;
    }

    public void onPreviewFinished(PreviewJob finishedPreview) {
        if (preview == finishedPreview) preview = null;
    }

    public static File latestReplay() {
        File[] files = ReplayPaths.replaysDir().listFiles(new java.io.FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isFile() && file.getName().endsWith(".lcr");
            }
        });
        if (files == null || files.length == 0) return null;
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        return files[0];
    }
}
