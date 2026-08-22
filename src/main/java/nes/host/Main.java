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
import javax.swing.JPanel;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 窗口、按键、音频。有声卡时按采样率限速。不解析 iNES。
 */
public final class Main {
    private static final int SCALE = 3;
    private static final long FRAME_NS = 16_666_667L;

    private Main() {}

    public static void main(String[] args) throws Exception {
        Path rom = args.length > 0 ? Path.of(args[0]) : pickRom();
        if (rom == null) {
            return;
        }
        Console console = new Console(Files.readAllBytes(rom));
        AtomicInteger buttons = new AtomicInteger();
        BufferedImage image = new BufferedImage(Ppu.WIDTH, Ppu.HEIGHT, BufferedImage.TYPE_INT_RGB);
        Screen screen = new Screen(image);

        JFrame frame = new JFrame("模拟器 — " + rom.getFileName());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(screen);
        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                buttons.updateAndGet(b -> b | mask(e.getKeyCode()));
            }

            @Override
            public void keyReleased(KeyEvent e) {
                buttons.updateAndGet(b -> b & ~mask(e.getKeyCode()));
            }
        });
        frame.pack();
        frame.setResizable(false);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.requestFocus();

        Thread loop = new Thread(() -> run(console, buttons, image, screen), "nes-loop");
        loop.setDaemon(true);
        loop.start();
    }

    private static void run(Console console, AtomicInteger buttons, BufferedImage image, Screen screen) {
        SourceDataLine line = openLine();
        short[] samples = new short[2048];
        byte[] pcm = new byte[4096];
        long next = System.nanoTime();
        try {
            while (true) {
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

    private static Path pickRom() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择 NES ROM");
        chooser.setFileFilter(new FileNameExtensionFilter("iNES (*.nes)", "nes"));
        int result = chooser.showOpenDialog(null);
        if (result != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        return chooser.getSelectedFile().toPath();
    }

    private static int mask(int keyCode) {
        return switch (keyCode) {
            case KeyEvent.VK_Z, KeyEvent.VK_Y -> 1;
            case KeyEvent.VK_X -> 2;
            case KeyEvent.VK_A, KeyEvent.VK_SHIFT -> 4;
            case KeyEvent.VK_ENTER -> 8;
            case KeyEvent.VK_UP -> 16;
            case KeyEvent.VK_DOWN -> 32;
            case KeyEvent.VK_LEFT -> 64;
            case KeyEvent.VK_RIGHT -> 128;
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
