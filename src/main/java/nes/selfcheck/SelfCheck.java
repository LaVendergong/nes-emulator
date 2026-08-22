package nes.selfcheck;

import nes.console.Console;
import nes.cart.Cartridge;
import nes.cart.InesRom;
import nes.host.Session;
import nes.host.KeyBindings;

/**
 * 钉住本切片不变量。失败即非零退出。
 */
public final class SelfCheck {
    private SelfCheck() {}

    public static void main(String[] args) {
        byte[] rom = nromRed();
        Console nes = new Console(rom);

        int peek = nes.peekCpu(0x8000);
        check(peek == 0x78, "$8000 应来自 cart PRG（SEI=$78），实际 $" + Integer.toHexString(peek));

        long cpu0 = nes.cpuCycles();
        long ppu0 = nes.ppuDots();
        for (int i = 0; i < 64; i++) {
            nes.stepInstruction();
        }
        long dc = nes.cpuCycles() - cpu0;
        long dp = nes.ppuDots() - ppu0;
        check(dc > 0, "CPU 应推进");
        check(dp == 3 * dc, "1 CPU cycle 必须对应 3 PPU dots，实际 cpu=" + dc + " ppu=" + dp);

        long frozenCpu = nes.cpuCycles();
        long frozenPpu = nes.ppuDots();
        try {
            Thread.sleep(30);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        check(nes.cpuCycles() == frozenCpu && nes.ppuDots() == frozenPpu, "墙钟流逝不得补 NES 步");

        Console restarted = new Console(rom);
        check(restarted.cpuCycles() < nes.cpuCycles(), "重启（新 Console）不得继承周期");
        check(restarted.peekCpu(0x8000) == 0x78, "重启后 $8000 仍来自同一盘");
        Session.verify(rom);
        KeyBindings.verify();

        for (int i = 0; i < 12; i++) {
            nes.stepFrame();
        }
        int pix = nes.framebuffer()[120 * 256 + 128];
        check(pix == 0xFFB53120, "第一帧中心应为调色板 $16，实际 0x" + Integer.toHexString(pix));

        byte[] shot = nes.saveState();
        long savedCpu = nes.cpuCycles();
        long savedPpu = nes.ppuDots();
        nes.stepFrame();
        check(nes.cpuCycles() != savedCpu, "存档后步进周期应变");
        nes.loadState(shot);
        check(nes.cpuCycles() == savedCpu && nes.ppuDots() == savedPpu, "读档应回到存档时的时钟");
        check(nes.framebuffer()[120 * 256 + 128] == pix, "读档应恢复帧缓冲");
        check(nes.peekCpu(0x8000) == 0x78, "读档后 $8000 仍来自 cart");

        boolean rejected = false;
        try {
            byte[] bad = rom.clone();
            bad[6] = 0x40;
            InesRom.load(bad);
        } catch (IllegalArgumentException e) {
            rejected = e.getMessage().contains("mapper");
        }
        check(rejected, "未支持的 mapper 应在 cart 边界拒绝");

        Cartridge mmc1 = InesRom.load(mmc1TwoBanks());
        check(mmc1.cpuRead(0x8000) == 0xAA, "MMC1 复位 $8000 应是 PRG 第 0 页");
        check(mmc1.cpuRead(0xC000) == 0xBB, "MMC1 复位 $C000 应固定最后一页");
        mmc1Serial(mmc1, 0xE000, 1);
        check(mmc1.cpuRead(0x8000) == 0xBB, "MMC1 切银行后 $8000 应是第 1 页");
        mmc1.clockCpu();
        mmc1.cpuWrite(0x8000, 0x80);
        int afterReset = mmc1.cpuRead(0x8000);
        mmc1.cpuWrite(0xE000, 1);
        mmc1.cpuWrite(0xE000, 1);
        mmc1.cpuWrite(0xE000, 1);
        mmc1.cpuWrite(0xE000, 1);
        mmc1.cpuWrite(0xE000, 1);
        check(mmc1.cpuRead(0x8000) == afterReset, "MMC1 连续周期写应丢掉，银行不变");

        Cartridge unrom = InesRom.load(unromTwoBanks());
        check(unrom.cpuRead(0x8000) == 0xAA, "UNROM 复位 $8000 应是第 0 页");
        check(unrom.cpuRead(0xC000) == 0xBB, "UNROM $C000 应固定最后一页");
        unrom.cpuWrite(0x8000, 1);
        check(unrom.cpuRead(0x8000) == 0xBB, "UNROM 切银行后 $8000 应是第 1 页");
        check(unrom.cpuRead(0xC000) == 0xBB, "UNROM 切银行后 $C000 仍是最后一页");

        nes.drainSamples();
        long audioCpu0 = nes.cpuCycles();
        for (int i = 0; i < 8; i++) {
            nes.stepFrame();
        }
        long audioDc = nes.cpuCycles() - audioCpu0;
        short[] silence = nes.drainSamples();
        int expect = (int) (audioDc * 44100L / 1_789_773);
        check(Math.abs(silence.length - expect) <= 1,
                "采样数应跟 CPU cycle 对齐，期望约 " + expect + " 实际 " + silence.length);

        Console beep = new Console(nromBeep());
        beep.drainSamples();
        for (int i = 0; i < 4; i++) {
            beep.stepFrame();
        }
        short[] tone = beep.drainSamples();
        int peak = 0;
        for (short s : tone) {
            peak = Math.max(peak, Math.abs(s));
        }
        check(peak > 200, "方波应产出非静音采样，峰值 " + peak);

        Console lax = new Console(nromLaxSax());
        for (int i = 0; i < 4; i++) {
            lax.stepFrame();
        }
        check(lax.peekCpu(0x0011) == 0xAA, "LAX/SAX 后 $11 应为 $AA，实际 $"
                + Integer.toHexString(lax.peekCpu(0x0011)));

        Console dmc = new Console(nromDmc());
        dmc.drainSamples();
        for (int i = 0; i < 8; i++) {
            dmc.stepFrame();
        }
        short[] pcm = dmc.drainSamples();
        int dmcPeak = 0;
        for (short s : pcm) {
            dmcPeak = Math.max(dmcPeak, Math.abs(s));
        }
        check(dmcPeak > 50, "DMC 应产出非静音采样，峰值 " + dmcPeak);

        System.out.println("selfcheck ok");
    }

    private static void mmc1Serial(Cartridge cart, int address, int value) {
        for (int i = 0; i < 5; i++) {
            cart.clockCpu();
            cart.cpuWrite(address, (value >> i) & 1);
        }
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    /** 等两次 vblank，写 $3F00=$16，开背景，停住。 */
    private static byte[] nromRed() {
        byte[] file = new byte[16 + 16384 + 8192];
        file[0] = 'N';
        file[1] = 'E';
        file[2] = 'S';
        file[3] = 0x1A;
        file[4] = 1;
        file[5] = 1;
        byte[] code = {
            0x78, (byte) 0xD8, (byte) 0xA2, (byte) 0xFF, (byte) 0x9A,
            (byte) 0xA9, 0x00, (byte) 0x8D, 0x00, 0x20, (byte) 0x8D, 0x01, 0x20,
            0x2C, 0x02, 0x20, 0x10, (byte) 0xFB,
            0x2C, 0x02, 0x20, 0x10, (byte) 0xFB,
            (byte) 0xA9, 0x3F, (byte) 0x8D, 0x06, 0x20,
            (byte) 0xA9, 0x00, (byte) 0x8D, 0x06, 0x20,
            (byte) 0xA9, 0x16, (byte) 0x8D, 0x07, 0x20,
            (byte) 0xA9, 0x0A, (byte) 0x8D, 0x01, 0x20,
            0x4C, 0x2B, (byte) 0x80
        };
        System.arraycopy(code, 0, file, 16, code.length);
        file[16 + 0x3FFC] = 0x00;
        file[16 + 0x3FFD] = (byte) 0x80;
        return file;
    }

    /** 32K PRG、CHR RAM、mapper 1：第 0 页 $AA，第 1 页 $BB。 */
    private static byte[] mmc1TwoBanks() {
        byte[] file = new byte[16 + 32768];
        file[0] = 'N';
        file[1] = 'E';
        file[2] = 'S';
        file[3] = 0x1A;
        file[4] = 2;
        file[5] = 0;
        file[6] = 0x10;
        file[16] = (byte) 0xAA;
        file[16 + 0x4000] = (byte) 0xBB;
        return file;
    }

    /** 开脉冲 1，停在死循环，用来钉「能出声」。 */
    private static byte[] nromBeep() {
        byte[] file = new byte[16 + 16384 + 8192];
        file[0] = 'N';
        file[1] = 'E';
        file[2] = 'S';
        file[3] = 0x1A;
        file[4] = 1;
        file[5] = 1;
        byte[] code = {
            0x78,
            (byte) 0xA9, 0x01, (byte) 0x8D, 0x15, 0x40,
            (byte) 0xA9, (byte) 0xBF, (byte) 0x8D, 0x00, 0x40,
            (byte) 0xA9, 0x00, (byte) 0x8D, 0x01, 0x40,
            (byte) 0xA9, (byte) 0xFD, (byte) 0x8D, 0x02, 0x40,
            (byte) 0xA9, 0x08, (byte) 0x8D, 0x03, 0x40,
            0x4C, 0x1A, (byte) 0x80
        };
        System.arraycopy(code, 0, file, 16, code.length);
        file[16 + 0x3FFC] = 0x00;
        file[16 + 0x3FFD] = (byte) 0x80;
        return file;
    }

    /** 32K PRG、CHR RAM、mapper 2：第 0 页 $AA，最后一页 $BB。 */
    private static byte[] unromTwoBanks() {
        byte[] file = new byte[16 + 32768];
        file[0] = 'N';
        file[1] = 'E';
        file[2] = 'S';
        file[3] = 0x1A;
        file[4] = 2;
        file[5] = 0;
        file[6] = 0x20;
        file[16] = (byte) 0xAA;
        file[16 + 0x4000] = (byte) 0xBB;
        return file;
    }

    /** LAX zp / SAX zp。 */
    private static byte[] nromLaxSax() {
        byte[] file = new byte[16 + 16384 + 8192];
        file[0] = 'N';
        file[1] = 'E';
        file[2] = 'S';
        file[3] = 0x1A;
        file[4] = 1;
        file[5] = 1;
        byte[] code = {
            (byte) 0xA9, (byte) 0xAA, (byte) 0x85, 0x10,
            (byte) 0xA7, 0x10, (byte) 0x87, 0x11,
            0x4C, 0x08, (byte) 0x80
        };
        System.arraycopy(code, 0, file, 16, code.length);
        file[16 + 0x3FFC] = 0x00;
        file[16 + 0x3FFD] = (byte) 0x80;
        return file;
    }

    /** 开 DMC，样本在 $C000。 */
    private static byte[] nromDmc() {
        byte[] file = new byte[16 + 16384 + 8192];
        file[0] = 'N';
        file[1] = 'E';
        file[2] = 'S';
        file[3] = 0x1A;
        file[4] = 1;
        file[5] = 1;
        byte[] code = {
            0x78,
            (byte) 0xA9, 0x4F, (byte) 0x8D, 0x10, 0x40,
            (byte) 0xA9, 0x01, (byte) 0x8D, 0x12, 0x40,
            (byte) 0xA9, 0x10, (byte) 0x8D, 0x13, 0x40,
            (byte) 0xA9, 0x10, (byte) 0x8D, 0x15, 0x40,
            0x4C, 0x15, (byte) 0x80
        };
        System.arraycopy(code, 0, file, 16, code.length);
        for (int i = 0x40; i < 0x3FFC; i++) {
            file[16 + i] = (byte) 0x55;
        }
        file[16 + 0x3FFC] = 0x00;
        file[16 + 0x3FFD] = (byte) 0x80;
        return file;
    }
}
