package nes.android;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import nes.save.SaveStore;

/** 虚拟键在屏幕上的位置（0–1）。NES bit 固定，不换功能。 */
final class PadMap {
    static final int DPAD = 0;
    static final int A = 1;
    static final int B = 2;
    static final int SELECT = 3;
    static final int START = 4;
    static final int COUNT = 5;
    private static final String[] KEY = {"DPAD", "A", "B", "SELECT", "START"};

    final float[] x = new float[COUNT];
    final float[] y = new float[COUNT];

    PadMap() {
        defaults();
    }

    static Path file() {
        return SaveStore.root.resolve("pad-layout.txt");
    }

    static PadMap load() {
        PadMap map = new PadMap();
        Path path = file();
        if (!Files.isRegularFile(path)) {
            return map;
        }
        try {
            boolean any = false;
            for (String line : Files.readAllLines(path)) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) {
                    continue;
                }
                int eq = t.indexOf('=');
                int comma = t.indexOf(',', eq);
                if (eq <= 0 || comma <= eq) {
                    continue;
                }
                int id = idOf(t.substring(0, eq).trim());
                if (id < 0) {
                    continue;
                }
                float nx = Float.parseFloat(t.substring(eq + 1, comma).trim());
                float ny = Float.parseFloat(t.substring(comma + 1).trim());
                if (nx <= 0 || nx >= 1 || ny <= 0 || ny >= 1) {
                    continue;
                }
                map.x[id] = nx;
                map.y[id] = ny;
                any = true;
            }
            if (!any) {
                map.defaults();
            }
        } catch (Exception e) {
            map.defaults();
        }
        return map;
    }

    void save() {
        try {
            Files.createDirectories(file().getParent());
            List<String> lines = new ArrayList<>();
            lines.add("# name=x,y  (0-1 of the pad overlay). bits stay A=1 B=2 Select=4 Start=8");
            for (int i = 0; i < COUNT; i++) {
                lines.add(KEY[i] + "=" + x[i] + "," + y[i]);
            }
            Files.write(file(), lines);
        } catch (Exception ignored) {
        }
    }

    void defaults() {
        x[DPAD] = 0.11f;
        y[DPAD] = 0.42f;
        x[SELECT] = 0.11f;
        y[SELECT] = 0.88f;
        x[B] = 0.835f;
        y[B] = 0.58f;
        x[A] = 0.92f;
        y[A] = 0.36f;
        x[START] = 0.89f;
        y[START] = 0.88f;
    }

    void copyFrom(PadMap other) {
        System.arraycopy(other.x, 0, x, 0, COUNT);
        System.arraycopy(other.y, 0, y, 0, COUNT);
    }

    private static int idOf(String key) {
        for (int i = 0; i < KEY.length; i++) {
            if (KEY[i].equals(key)) {
                return i;
            }
        }
        return -1;
    }
}
