package nes.android;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.PopupMenu;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import nes.apu.Apu;
import nes.console.Console;
import nes.save.SaveStore;

/**
 * Android Host：选盘、暂停、重启、存档槽、拖键位、贴图层、播采样。不拆 iNES。
 */
public final class MainActivity extends Activity {
    private static final int OPEN_ROM = 1;
    private static final long FRAME_NS = 16_666_667L;
    private static final int FB_W = 256;
    private static final int FB_H = 240;

    private ScreenView screen;
    private PadView pad;
    private PadMap padMap;
    private final PadMap padBackup = new PadMap();
    private boolean wasPausedBeforeEdit;
    private final Object lock = new Object();
    private Console console;
    private byte[] rom;
    private String romName;
    private volatile boolean userPaused;
    private volatile boolean stopped;
    private volatile boolean foreground = true;
    private AudioTrack track;
    private Thread loop;
    private int framesWritten;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SaveStore.bindDir(getFilesDir().toPath().resolve("saves"));
        setContentView(R.layout.activity_main);
        hideSystemBars();
        screen = findViewById(R.id.screen);
        pad = findViewById(R.id.pad);
        padMap = PadMap.load();
        pad.bind(padMap);
        findViewById(R.id.menu).setOnClickListener(this::showMenu);
        track = openTrack();
        loop = new Thread(this::runLoop, "nes-loop");
        loop.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        foreground = true;
        if (track != null) {
            track.play();
        }
    }

    @Override
    protected void onPause() {
        foreground = false;
        if (track != null) {
            track.pause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopped = true;
        loop.interrupt();
        if (track != null) {
            track.release();
            track = null;
        }
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController bars = getWindow().getInsetsController();
            if (bars != null) {
                bars.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                bars.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
            return;
        }
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    private void showMenu(View anchor) {
        PopupMenu pop = new PopupMenu(this, anchor);
        if (pad.editing()) {
            pop.getMenu().add(0, 10, 0, "完成");
            pop.getMenu().add(0, 11, 0, "恢复默认");
            pop.getMenu().add(0, 12, 0, "取消");
            pop.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 10 -> finishEdit(true);
                    case 11 -> {
                        padMap.defaults();
                        pad.bind(padMap);
                    }
                    case 12 -> finishEdit(false);
                    default -> {
                        return false;
                    }
                }
                return true;
            });
            pop.show();
            return;
        }
        pop.getMenu().add(0, 1, 0, "打开 ROM");
        pop.getMenu().add(0, 2, 0, userPaused ? "继续" : "暂停");
        pop.getMenu().add(0, 3, 0, "重启");
        pop.getMenu().add(0, 4, 0, "存档...");
        pop.getMenu().add(0, 5, 0, "调整位置...");
        pop.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1 -> openRom();
                case 2 -> togglePause();
                case 3 -> restart();
                case 4 -> SaveSlots.show(this);
                case 5 -> startEdit();
                default -> {
                    return false;
                }
            }
            return true;
        });
        pop.show();
    }

    private void startEdit() {
        padBackup.copyFrom(padMap);
        wasPausedBeforeEdit = pauseForDialog();
        pad.setEditing(true);
        toast("拖动按键。完成后打开菜单。");
    }

    private void finishEdit(boolean keep) {
        if (keep) {
            padMap.save();
        } else {
            padMap.copyFrom(padBackup);
            pad.bind(padMap);
        }
        pad.setEditing(false);
        endDialog(wasPausedBeforeEdit, false);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != OPEN_ROM || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) {
                toast("无法读取 ROM");
                return;
            }
            byte[] bytes = readAll(in);
            String name = displayName(uri);
            synchronized (lock) {
                rom = bytes;
                romName = name;
                console = new Console(bytes);
                userPaused = false;
            }
            refreshTitle();
            flushTrack();
        } catch (Exception e) {
            toast(e.getMessage() == null ? "ROM 无效" : e.getMessage());
        }
    }

    private void openRom() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, OPEN_ROM);
    }

    private void togglePause() {
        synchronized (lock) {
            if (console == null) {
                return;
            }
            userPaused = !userPaused;
        }
        refreshTitle();
    }

    private void restart() {
        synchronized (lock) {
            if (rom == null) {
                return;
            }
            console = new Console(rom);
            userPaused = false;
        }
        refreshTitle();
        flushTrack();
    }

    String romName() {
        return romName;
    }

    boolean pauseForDialog() {
        synchronized (lock) {
            boolean was = userPaused;
            if (console != null) {
                userPaused = true;
            }
            refreshTitle();
            return was;
        }
    }

    void endDialog(boolean wasPaused, boolean loaded) {
        synchronized (lock) {
            userPaused = console != null && !loaded && wasPaused;
        }
        refreshTitle();
        hideSystemBars();
    }

    void writeSlot(int slot) throws Exception {
        synchronized (lock) {
            if (console == null || romName == null) {
                throw new IllegalStateException("先打开 ROM");
            }
            SaveStore.write(romName, slot, console.saveState(), console.framebuffer(), FB_W, FB_H);
        }
    }

    void loadSlot(int slot) throws Exception {
        if (romName == null) {
            throw new IllegalStateException("先打开 ROM");
        }
        byte[] state = SaveStore.readState(romName, slot);
        synchronized (lock) {
            if (console == null) {
                throw new IllegalStateException("先打开 ROM");
            }
            console.loadState(state);
            userPaused = false;
        }
        flushTrack();
    }

    private void refreshTitle() {
        if (romName == null) {
            setTitle("FC-NES");
            return;
        }
        setTitle(userPaused ? "模拟器 — " + romName + "（已暂停）" : "模拟器 — " + romName);
    }

    private void runLoop() {
        short[] samples = new short[2048];
        byte[] pcm = new byte[4096];
        long next = System.nanoTime();
        try {
            while (!stopped) {
                boolean pause;
                int n = 0;
                int[] frame = null;
                synchronized (lock) {
                    pause = console == null || userPaused || !foreground;
                    if (!pause) {
                        console.setButtons(pad.mask());
                        console.stepFrame();
                        frame = console.framebuffer();
                        n = console.drainSamples(samples);
                    }
                }
                if (frame != null) {
                    screen.blit(frame);
                }
                if (pause) {
                    Thread.sleep(FRAME_NS / 1_000_000L);
                    next = System.nanoTime();
                    continue;
                }
                writeAudio(samples, pcm, n);
                if (track == null) {
                    next += FRAME_NS;
                    long now = System.nanoTime();
                    if (next > now) {
                        Thread.sleep((next - now) / 1_000_000L);
                    } else {
                        next = now;
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private AudioTrack openTrack() {
        int min = AudioTrack.getMinBufferSize(
                Apu.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) {
            return null;
        }
        int size = Math.max(min, Apu.SAMPLE_RATE / 12 * 2);
        AudioTrack line = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(Apu.SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(size)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
        line.play();
        return line;
    }

    private void flushTrack() {
        if (track == null) {
            return;
        }
        track.flush();
        framesWritten = track.getPlaybackHeadPosition();
    }

    private void writeAudio(short[] samples, byte[] pcm, int n) {
        if (track == null || n <= 0) {
            return;
        }
        int queued = framesWritten - track.getPlaybackHeadPosition();
        if (queued > Apu.SAMPLE_RATE / 10) {
            track.pause();
            track.flush();
            track.play();
            framesWritten = track.getPlaybackHeadPosition();
        }
        int off = 0;
        while (off < n) {
            int chunk = Math.min(n - off, pcm.length / 2);
            for (int i = 0; i < chunk; i++) {
                int s = samples[off + i];
                pcm[i * 2] = (byte) s;
                pcm[i * 2 + 1] = (byte) (s >> 8);
            }
            int bytes = chunk * 2;
            int written = 0;
            while (written < bytes) {
                int w = track.write(pcm, written, bytes - written, AudioTrack.WRITE_BLOCKING);
                if (w <= 0) {
                    return;
                }
                written += w;
            }
            framesWritten += chunk;
            off += chunk;
        }
    }

    private String displayName(Uri uri) {
        try (android.database.Cursor c = getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                return c.getString(0);
            }
        } catch (Exception ignored) {
        }
        return "FC-NES";
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
