package nes.host;

import nes.apu.Apu;
import nes.console.Console;
import nes.ppu.Ppu;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 窗口、按键、音频、会话控制。有声卡时按采样率限速。不解析 iNES。
 */
public final class Main {
    private static final int SCALE = 3;
    private static final long FRAME_NS = 16_666_667L;

    private Main() {}

    public static void main(String[] args) throws Exception {
        Path rom = args.length > 0 ? Path.of(args[0]) : pickRom(null);
        if (rom == null) {
            return;
        }
        Session session = new Session(Files.readAllBytes(rom), rom.getFileName().toString());
        KeyBindings keys = KeyBindings.load();
        AtomicInteger buttons = new AtomicInteger();
        AtomicInteger hostHeld = new AtomicInteger();
        AtomicBoolean choosing = new AtomicBoolean();
        AtomicBoolean savingUi = new AtomicBoolean();
        AtomicBoolean keysUi = new AtomicBoolean();
        Runnable[] saveRefresh = {null};
        BufferedImage image = new BufferedImage(Ppu.WIDTH, Ppu.HEIGHT, BufferedImage.TYPE_INT_RGB);
        Screen screen = new Screen(image);

        JFrame frame = new JFrame(session.title());
        JMenuItem pauseItem = new JMenuItem("暂停 (P)");
        frame.setJMenuBar(menuBar(frame, session, pauseItem, choosing, savingUi, saveRefresh, keys, keysUi, buttons));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(screen);
        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int code = e.getKeyCode();
                int bit = hostBit(code);
                if (bit != 0) {
                    if ((hostHeld.getAndUpdate(h -> h | bit) & bit) != 0) {
                        return;
                    }
                    if (code == KeyEvent.VK_O) {
                        chooseAndLoad(frame, session, choosing);
                    } else if (code == KeyEvent.VK_P || code == KeyEvent.VK_SPACE) {
                        togglePause(frame, session, pauseItem);
                    } else if (code == KeyEvent.VK_R) {
                        session.requestRestart();
                    } else if (code == KeyEvent.VK_F5) {
                        openSaves(frame, session, pauseItem, savingUi, saveRefresh);
                    }
                    return;
                }
                buttons.updateAndGet(b -> b | keys.mask(code));
            }

            @Override
            public void keyReleased(KeyEvent e) {
                int bit = hostBit(e.getKeyCode());
                if (bit != 0) {
                    hostHeld.updateAndGet(h -> h & ~bit);
                    return;
                }
                buttons.updateAndGet(b -> b & ~keys.mask(e.getKeyCode()));
            }
        });
        frame.pack();
        frame.setResizable(false);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.requestFocus();

        Thread loop = new Thread(() -> run(session, buttons, image, screen, frame, pauseItem, saveRefresh), "nes-loop");
        loop.setDaemon(true);
        loop.start();
    }

    private static JMenuBar menuBar(
            JFrame frame,
            Session session,
            JMenuItem pauseItem,
            AtomicBoolean choosing,
            AtomicBoolean savingUi,
            Runnable[] saveRefresh,
            KeyBindings keys,
            AtomicBoolean keysUi,
            AtomicInteger buttons
    ) {
        JMenuItem open = new JMenuItem("打开 ROM... (O)");
        open.addActionListener(e -> chooseAndLoad(frame, session, choosing));
        pauseItem.addActionListener(e -> togglePause(frame, session, pauseItem));
        JMenuItem restart = new JMenuItem("重新开始 (R)");
        restart.addActionListener(e -> session.requestRestart());
        JMenuItem saves = new JMenuItem("存档... (F5)");
        saves.addActionListener(e -> openSaves(frame, session, pauseItem, savingUi, saveRefresh));
        JMenuItem remap = new JMenuItem("切换按键...");
        remap.addActionListener(e -> openKeys(frame, session, pauseItem, keys, keysUi, buttons));
        JMenu game = new JMenu("游戏");
        game.add(open);
        game.add(pauseItem);
        game.add(restart);
        game.add(saves);
        game.add(remap);
        JMenuBar bar = new JMenuBar();
        bar.add(game);
        return bar;
    }

    private static void togglePause(JFrame frame, Session session, JMenuItem pauseItem) {
        session.togglePause();
        refreshChrome(frame, session, pauseItem);
        frame.requestFocus();
    }

    private static void openKeys(
            JFrame frame,
            Session session,
            JMenuItem pauseItem,
            KeyBindings keys,
            AtomicBoolean busy,
            AtomicInteger buttons
    ) {
        if (!busy.compareAndSet(false, true)) {
            return;
        }
        boolean wasPaused = session.paused();
        session.setPaused(true);
        buttons.set(0);
        refreshChrome(frame, session, pauseItem);
        try {
            KeyboardDialog.show(frame, keys);
        } finally {
            session.setPaused(wasPaused);
            refreshChrome(frame, session, pauseItem);
            busy.set(false);
            frame.requestFocus();
        }
    }

    private static void openSaves(
            JFrame frame,
            Session session,
            JMenuItem pauseItem,
            AtomicBoolean busy,
            Runnable[] saveRefresh
    ) {
        if (!busy.compareAndSet(false, true)) {
            return;
        }
        boolean wasPaused = session.paused();
        session.setPaused(true);
        refreshChrome(frame, session, pauseItem);
        try {
            boolean loaded = SaveDialog.show(frame, session, saveRefresh);
            if (!loaded) {
                session.setPaused(wasPaused);
                refreshChrome(frame, session, pauseItem);
            }
        } finally {
            busy.set(false);
            frame.requestFocus();
        }
    }

    private static void chooseAndLoad(JFrame frame, Session session, AtomicBoolean choosing) {
        if (!choosing.compareAndSet(false, true)) {
            return;
        }
        try {
            Path path = pickRom(frame);
            if (path == null) {
                return;
            }
            session.requestLoad(Files.readAllBytes(path), path.getFileName().toString());
        } catch (IOException e) {
            System.err.println("无法读取 ROM：" + e.getMessage());
        } finally {
            choosing.set(false);
            frame.requestFocus();
        }
    }

    private static void refreshChrome(JFrame frame, Session session, JMenuItem pauseItem) {
        frame.setTitle(session.title());
        pauseItem.setText(session.paused() ? "继续 (P)" : "暂停 (P)");
    }

    private static void run(
            Session session,
            AtomicInteger buttons,
            BufferedImage image,
            Screen screen,
            JFrame frame,
            JMenuItem pauseItem,
            Runnable[] saveRefresh
    ) {
        SourceDataLine line = openLine();
        short[] samples = new short[2048];
        byte[] pcm = new byte[4096];
        long next = System.nanoTime();
        try {
            while (true) {
                Console console = session.apply();
                boolean replaced = session.takeReplaced();
                if (session.takeTitleDirty()) {
                    SwingUtilities.invokeLater(() -> refreshChrome(frame, session, pauseItem));
                }
                if (session.takeSavesDirty()) {
                    Runnable refresh = saveRefresh[0];
                    if (refresh != null) {
                        SwingUtilities.invokeLater(refresh);
                    }
                }
                if (replaced) {
                    int[] pix = console.framebuffer();
                    image.setRGB(0, 0, Ppu.WIDTH, Ppu.HEIGHT, pix, 0, Ppu.WIDTH);
                    screen.repaint();
                    if (line != null) {
                        line.flush();
                    }
                }
                if (session.paused()) {
                    screen.repaint();
                    // ponytail: 暂停只闲置 Host，不累加墙钟债。NES 时间停在上次 stepFrame。
                    Thread.sleep(FRAME_NS / 1_000_000L);
                    next = System.nanoTime();
                    continue;
                }
                console.setButtons(buttons.get());
                console.stepFrame();
                int[] pix = console.framebuffer();
                image.setRGB(0, 0, Ppu.WIDTH, Ppu.HEIGHT, pix, 0, Ppu.WIDTH);
                screen.repaint();
                int n = console.drainSamples(samples);
                writeAudio(line, samples, pcm, n);
                // ponytail: 有声卡时用 write 阻塞限速（对齐 44100）。墙钟 60Hz 会比 NES 略慢，缓冲见底就卡一下。
                if (line == null) {
                    next += FRAME_NS;
                    long now = System.nanoTime();
                    if (next > now) {
                        Thread.sleep((next - now) / 1_000_000L);
                    } else {
                        next = now;
                    }
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            if (line != null) {
                line.close();
            }
        }
    }

    private static SourceDataLine openLine() {
        AudioFormat format = new AudioFormat(Apu.SAMPLE_RATE, 16, 1, true, false);
        try {
            SourceDataLine line = AudioSystem.getSourceDataLine(format);
            line.open(format, Apu.SAMPLE_RATE / 15 * 2);
            line.start();
            return line;
        } catch (LineUnavailableException e) {
            System.err.println("音频不可用，继续静音：" + e.getMessage());
            return null;
        }
    }

    private static void writeAudio(SourceDataLine line, short[] samples, byte[] pcm, int n) {
        if (line == null || n <= 0) {
            return;
        }
        int bytes = n * 2;
        if (bytes > pcm.length) {
            n = pcm.length / 2;
            bytes = n * 2;
        }
        for (int i = 0; i < n; i++) {
            pcm[i * 2] = (byte) samples[i];
            pcm[i * 2 + 1] = (byte) (samples[i] >> 8);
        }
        line.write(pcm, 0, bytes);
    }

    private static Path pickRom(Component parent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择 NES ROM");
        chooser.setFileFilter(new FileNameExtensionFilter("iNES (*.nes)", "nes"));
        Path roms = Path.of("roms");
        if (Files.isDirectory(roms)) {
            chooser.setCurrentDirectory(roms.toFile());
        }
        int result = chooser.showOpenDialog(parent);
        if (result != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        return chooser.getSelectedFile().toPath();
    }

    private static int hostBit(int keyCode) {
        return switch (keyCode) {
            case KeyEvent.VK_O -> 1;
            case KeyEvent.VK_P, KeyEvent.VK_SPACE -> 2;
            case KeyEvent.VK_R -> 4;
            case KeyEvent.VK_F5 -> 8;
            default -> 0;
        };
    }

    private static final class Screen extends JPanel {
        private final BufferedImage image;

        Screen(BufferedImage image) {
            this.image = image;
            setPreferredSize(new Dimension(Ppu.WIDTH * SCALE, Ppu.HEIGHT * SCALE));
            setFocusable(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.drawImage(image, 0, 0, Ppu.WIDTH * SCALE, Ppu.HEIGHT * SCALE, null);
        }
    }
}
