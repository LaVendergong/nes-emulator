package nes.host;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Host 手柄键表。O/P/空格/R/F5/Esc 留给会话，不能绑 NES。
 */
public final class KeyBindings {
    static final int A = 1;
    static final int B = 2;
    static final int SELECT = 4;
    static final int START = 8;
    static final int UP = 16;
    static final int DOWN = 32;
    static final int LEFT = 64;
    static final int RIGHT = 128;

    static final Path FILE = Path.of("saves", "keys.txt");

    private final Map<Integer, Integer> map = new LinkedHashMap<>();
    private Path file;

    static KeyBindings defaults() {
        KeyBindings k = new KeyBindings();
        k.map.put(KeyEvent.VK_Z, A);
        k.map.put(KeyEvent.VK_Y, A);
        k.map.put(KeyEvent.VK_X, B);
        k.map.put(KeyEvent.VK_A, SELECT);
        k.map.put(KeyEvent.VK_SHIFT, SELECT);
        k.map.put(KeyEvent.VK_ENTER, START);
        k.map.put(KeyEvent.VK_UP, UP);
        k.map.put(KeyEvent.VK_DOWN, DOWN);
        k.map.put(KeyEvent.VK_LEFT, LEFT);
        k.map.put(KeyEvent.VK_RIGHT, RIGHT);
        return k;
    }

    static KeyBindings load() {
        KeyBindings k = defaults();
        k.file = FILE;
        if (!Files.isRegularFile(FILE)) {
            return k;
        }
        try {
            Map<Integer, Integer> loaded = new LinkedHashMap<>();
            for (String line : Files.readAllLines(FILE)) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) {
                    continue;
                }
                int eq = t.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                int code = Integer.parseInt(t.substring(0, eq).trim());
                int pad = Integer.parseInt(t.substring(eq + 1).trim());
                if (isReserved(code) || padName(pad) == null) {
                    continue;
                }
                loaded.put(code, pad);
            }
            if (!loaded.isEmpty()) {
                k.map.clear();
                k.map.putAll(loaded);
            }
            return k;
        } catch (Exception e) {
            return k;
        }
    }

    void save() {
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(FILE.getParent());
            List<String> lines = new ArrayList<>();
            lines.add("# keyCode=padBit  A=1 B=2 Select=4 Start=8 Up=16 Down=32 Left=64 Right=128");
            for (Map.Entry<Integer, Integer> e : map.entrySet()) {
                lines.add(e.getKey() + "=" + e.getValue());
            }
            Files.write(FILE, lines);
        } catch (IOException e) {
            System.err.println("无法保存按键绑定：" + e.getMessage());
        }
    }

    int mask(int keyCode) {
        return map.getOrDefault(keyCode, 0);
    }

    boolean isBound(int keyCode) {
        return map.containsKey(keyCode);
    }

    List<Map.Entry<Integer, Integer>> entries() {
        return List.copyOf(map.entrySet());
    }

    void resetDefaults() {
        map.clear();
        map.putAll(defaults().map);
        save();
    }

    /**
     * 把 {@code oldKey} 的手柄功能绑到 {@code newKey}。
     * 新键空着就挪过去；已被占用则对调，避免某个功能没键。
     */
    boolean rebind(int oldKey, int newKey) {
        if (!map.containsKey(oldKey) || isReserved(newKey)) {
            return false;
        }
        if (oldKey == newKey) {
            return true;
        }
        int action = map.get(oldKey);
        Integer other = map.get(newKey);
        map.remove(oldKey);
        if (other != null) {
            map.remove(newKey);
            map.put(newKey, action);
            map.put(oldKey, other);
        } else {
            map.put(newKey, action);
        }
        save();
        return true;
    }

    static boolean isReserved(int keyCode) {
        return keyCode == KeyEvent.VK_O
                || keyCode == KeyEvent.VK_P
                || keyCode == KeyEvent.VK_SPACE
                || keyCode == KeyEvent.VK_R
                || keyCode == KeyEvent.VK_F5
                || keyCode == KeyEvent.VK_ESCAPE;
    }

    static String padName(int pad) {
        return switch (pad) {
            case A -> "A";
            case B -> "B";
            case SELECT -> "选择";
            case START -> "开始";
            case UP -> "↑";
            case DOWN -> "↓";
            case LEFT -> "←";
            case RIGHT -> "→";
            default -> null;
        };
    }

    static String keyName(int keyCode) {
        return KeyEvent.getKeyText(keyCode);
    }

    public static void verify() {
        KeyBindings k = defaults();
        if (k.mask(KeyEvent.VK_Z) != A || k.mask(KeyEvent.VK_X) != B) {
            throw new AssertionError("默认 Z=A X=B");
        }
        if (!k.rebind(KeyEvent.VK_Z, KeyEvent.VK_Q)) {
            throw new AssertionError("应能换到空键");
        }
        if (k.mask(KeyEvent.VK_Q) != A || k.mask(KeyEvent.VK_Z) != 0) {
            throw new AssertionError("换绑后旧键应松开");
        }
        if (!k.rebind(KeyEvent.VK_Q, KeyEvent.VK_X)) {
            throw new AssertionError("占键应对调");
        }
        if (k.mask(KeyEvent.VK_X) != A || k.mask(KeyEvent.VK_Q) != B) {
            throw new AssertionError("对调后双方都还要有键");
        }
        if (k.rebind(KeyEvent.VK_X, KeyEvent.VK_P)) {
            throw new AssertionError("Host 热键不能绑手柄");
        }
        if (k.mask(KeyEvent.VK_P) != 0 || k.mask(KeyEvent.VK_X) != A) {
            throw new AssertionError("拒绝热键后绑定不变");
        }
    }
}
