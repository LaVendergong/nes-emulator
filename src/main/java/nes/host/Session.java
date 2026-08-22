package nes.host;

import nes.console.Console;

/**
 * 一局会话：换盘/重启是 {@code new Console(ines)}，暂停只挡步进。
 */
public final class Session {
    private final Object lock = new Object();
    private Console console;
    private byte[] rom;
    private String name;
    private boolean paused;
    private byte[] pendingRom;
    private String pendingName;
    private boolean pendingRestart;
    private Integer pendingSaveSlot;
    private byte[] pendingLoadState;
    private boolean replaced;
    private boolean titleDirty;
    private boolean savesDirty;

    Session(byte[] rom, String name) {
        this.rom = rom;
        this.name = name;
        this.console = new Console(rom);
    }

    void requestLoad(byte[] ines, String name) {
        synchronized (lock) {
            pendingRom = ines;
            pendingName = name;
        }
    }

    void requestRestart() {
        synchronized (lock) {
            pendingRestart = true;
        }
    }

    void requestSave(int slot) {
        synchronized (lock) {
            pendingSaveSlot = slot;
        }
    }

    void requestLoadState(byte[] state) {
        synchronized (lock) {
            pendingLoadState = state;
        }
    }

    String romName() {
        synchronized (lock) {
            return name;
        }
    }

    void setPaused(boolean value) {
        synchronized (lock) {
            if (paused == value) {
                return;
            }
            paused = value;
            titleDirty = true;
        }
    }

    void togglePause() {
        synchronized (lock) {
            paused = !paused;
            titleDirty = true;
        }
    }

    boolean paused() {
        synchronized (lock) {
            return paused;
        }
    }

    String title() {
        synchronized (lock) {
            return paused ? "模拟器 — " + name + "（已暂停）" : "模拟器 — " + name;
        }
    }

    Console apply() {
        synchronized (lock) {
            replaced = false;
            if (pendingRom != null) {
                try {
                    console = new Console(pendingRom);
                    rom = pendingRom;
                    name = pendingName;
                    paused = false;
                    replaced = true;
                    titleDirty = true;
                } catch (RuntimeException e) {
                    System.err.println("无法加载 ROM：" + e.getMessage());
                }
                pendingRom = null;
                pendingName = null;
                pendingRestart = false;
                pendingSaveSlot = null;
                pendingLoadState = null;
            } else if (pendingRestart) {
                console = new Console(rom);
                paused = false;
                pendingRestart = false;
                pendingSaveSlot = null;
                pendingLoadState = null;
                replaced = true;
                titleDirty = true;
            }
            if (pendingSaveSlot != null) {
                try {
                    SaveStore.write(
                            name,
                            pendingSaveSlot,
                            console.saveState(),
                            console.framebuffer(),
                            256,
                            240
                    );
                    savesDirty = true;
                } catch (Exception e) {
                    System.err.println("无法保存：" + e.getMessage());
                }
                pendingSaveSlot = null;
            }
            if (pendingLoadState != null) {
                try {
                    console.loadState(pendingLoadState);
                    paused = false;
                    replaced = true;
                    titleDirty = true;
                } catch (RuntimeException e) {
                    System.err.println("无法读档：" + e.getMessage());
                }
                pendingLoadState = null;
            }
            return console;
        }
    }

    boolean takeReplaced() {
        synchronized (lock) {
            boolean v = replaced;
            replaced = false;
            return v;
        }
    }

    boolean takeTitleDirty() {
        synchronized (lock) {
            boolean v = titleDirty;
            titleDirty = false;
            return v;
        }
    }

    boolean takeSavesDirty() {
        synchronized (lock) {
            boolean v = savesDirty;
            savesDirty = false;
            return v;
        }
    }

    public static void verify(byte[] rom) {
        Session s = new Session(rom, "t");
        Console c = s.apply();
        for (int i = 0; i < 8; i++) {
            c.stepInstruction();
        }
        long advanced = c.cpuCycles();
        s.togglePause();
        if (!s.paused() || s.apply().cpuCycles() != advanced) {
            throw new AssertionError("暂停不得推进 NES 时间");
        }
        s.requestRestart();
        Console restarted = s.apply();
        if (s.paused() || restarted.cpuCycles() >= advanced) {
            throw new AssertionError("重启应是新机器并取消暂停");
        }
        s.requestLoad(rom, "t2");
        Console swapped = s.apply();
        if (swapped == restarted || swapped.peekCpu(0x8000) != 0x78) {
            throw new AssertionError("换盘应换一台机器，PRG 仍来自 cart");
        }
        try {
            SaveStore.verify();
        } catch (Exception e) {
            throw new AssertionError("槽位覆盖/上限", e);
        }
    }
}
