package nes.save;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 槽位文件。Host 不拆 NES 快照，只包一层时间与缩略图。
 */
public final class SaveStore {
    public static final int DEFAULT_SLOTS = 10;
    public static final int MIN_SLOTS = 1;
    public static final int MAX_SLOTS = 30;
    private static final int MAGIC = 0x534C4F54;
    private static final int VERSION = 1;
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    public static Path root = Path.of("saves");

    private SaveStore() {}

    /** jpackage 启动时工作目录不可靠，存档跟 exe 放一起。 */
    public static void bindAppDir() {
        String launcher = System.getProperty("jpackage.app-path");
        if (launcher == null || launcher.isBlank()) {
            return;
        }
        root = Path.of(launcher).toAbsolutePath().getParent().resolve("saves");
    }

    public static void bindDir(Path dir) {
        root = dir;
    }

    public static int slotLimit() {
        Path file = root.resolve("slot-limit.txt");
        if (!Files.isRegularFile(file)) {
            return DEFAULT_SLOTS;
        }
        try {
            int n = Integer.parseInt(new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim());
            return Math.max(MIN_SLOTS, Math.min(MAX_SLOTS, n));
        } catch (Exception e) {
            return DEFAULT_SLOTS;
        }
    }

    public static void setSlotLimit(int n) {
        n = Math.max(MIN_SLOTS, Math.min(MAX_SLOTS, n));
        try {
            Files.createDirectories(root);
            Files.write(root.resolve("slot-limit.txt"), Integer.toString(n).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("无法保存槽位上限：" + e.getMessage());
        }
    }

    public static Slot meta(String romName, int slot) {
        Path path = path(romName, slot);
        if (!Files.isRegularFile(path)) {
            return Slot.empty(slot);
        }
        try (DataInputStream in = new DataInputStream(Files.newInputStream(path))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) {
                return Slot.empty(slot);
            }
            long time = in.readLong();
            in.readUTF();
            int w = in.readInt();
            int h = in.readInt();
            int[] thumb = new int[w * h];
            for (int i = 0; i < thumb.length; i++) {
                thumb[i] = in.readInt();
            }
            return new Slot(slot, false, time, w, h, thumb);
        } catch (IOException e) {
            return Slot.empty(slot);
        }
    }

    public static byte[] readState(String romName, int slot) throws IOException {
        try (DataInputStream in = new DataInputStream(Files.newInputStream(path(romName, slot)))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) {
                throw new IOException("不是本模拟器槽位文件");
            }
            in.readLong();
            in.readUTF();
            int w = in.readInt();
            int h = in.readInt();
            skipFully(in, (long) w * h * 4);
            int n = in.readInt();
            byte[] state = new byte[n];
            in.readFully(state);
            return state;
        }
    }

    public static void write(String romName, int slot, byte[] state, int[] framebuffer, int width, int height)
            throws IOException {
        Path path = path(romName, slot);
        Files.createDirectories(path.getParent());
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(path))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeLong(System.currentTimeMillis());
            out.writeUTF(romName);
            out.writeInt(width);
            out.writeInt(height);
            int n = Math.min(framebuffer.length, width * height);
            for (int i = 0; i < n; i++) {
                out.writeInt(framebuffer[i]);
            }
            out.writeInt(state.length);
            out.write(state);
        }
    }

    public static String formatTime(long epochMs) {
        return TIME.format(Instant.ofEpochMilli(epochMs));
    }

    public static void verify() throws IOException {
        Path old = root;
        root = Files.createTempDirectory("nes-slots");
        try {
            int[] fb = new int[8];
            fb[0] = 0xFF112233;
            write("t.nes", 1, new byte[] {9, 8, 7}, fb, 2, 2);
            write("t.nes", 1, new byte[] {1, 2}, fb, 2, 2);
            Slot s = meta("t.nes", 1);
            if (s.empty || s.thumb[0] != 0xFF112233) {
                throw new AssertionError("覆盖后槽位应仍可读");
            }
            byte[] state = readState("t.nes", 1);
            if (state.length != 2 || state[0] != 1 || state[1] != 2) {
                throw new AssertionError("覆盖应换成新快照");
            }
            if (!meta("t.nes", 2).empty) {
                throw new AssertionError("空槽应为空");
            }
            setSlotLimit(7);
            if (slotLimit() != 7) {
                throw new AssertionError("槽位上限应能调整");
            }
        } finally {
            root = old;
        }
    }

    private static void skipFully(DataInputStream in, long bytes) throws IOException {
        byte[] dump = new byte[8192];
        long left = bytes;
        while (left > 0) {
            int n = in.read(dump, 0, (int) Math.min(left, dump.length));
            if (n < 0) {
                throw new IOException("槽位文件截断");
            }
            left -= n;
        }
    }

    private static Path path(String romName, int slot) {
        return root.resolve(key(romName)).resolve("slot-" + slot + ".sav");
    }

    private static String key(String name) {
        String s = name;
        for (char c : new char[] {'/', '\\', ':', '*', '?', '"', '<', '>', '|'}) {
            s = s.replace(c, '_');
        }
        return s.isEmpty() ? "rom" : s;
    }

    public static final class Slot {
        public final int index;
        public final boolean empty;
        public final long time;
        public final int width;
        public final int height;
        public final int[] thumb;

        Slot(int index, boolean empty, long time, int width, int height, int[] thumb) {
            this.index = index;
            this.empty = empty;
            this.time = time;
            this.width = width;
            this.height = height;
            this.thumb = thumb;
        }

        static Slot empty(int index) {
            return new Slot(index, true, 0, 0, 0, new int[0]);
        }
    }
}
