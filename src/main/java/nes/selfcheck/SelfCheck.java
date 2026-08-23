package nes.selfcheck;

import nes.console.Console;
import nes.cart.Cartridge;
import nes.cart.InesRom;
import nes.cart.Ym2413;
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

        Console palNes = new Console(nromPal());
        palNes.stepFrame();
        long palD0 = palNes.ppuDots();
        long palC0 = palNes.cpuCycles();
        palNes.stepFrame();
        long palDd = palNes.ppuDots() - palD0;
        long palDc = palNes.cpuCycles() - palC0;
        check(palDd >= 312L * 341 && palDd < 312L * 341 + 24,
                "PAL 一帧应约 312×341 dots，实际 " + palDd);
        check(Math.abs(palDd * 5 - palDc * 16) <= 16, "PAL 5 CPU 应约 16 PPU dots");
        palNes.drainSamples();
        long palA0 = palNes.cpuCycles();
        palNes.stepFrame();
        int palExpect = (int) ((palNes.cpuCycles() - palA0) * 44100L / 1_662_607);
        check(Math.abs(palNes.drainSamples().length - palExpect) <= 1, "PAL 采样应跟 1.662607 MHz 对齐");

        Console dendy = new Console(nromDendy());
        dendy.stepFrame();
        long denD0 = dendy.ppuDots();
        long denC0 = dendy.cpuCycles();
        dendy.stepFrame();
        long denDd = dendy.ppuDots() - denD0;
        long denDc = dendy.cpuCycles() - denC0;
        check(denDd >= 312L * 341 && denDd < 312L * 341 + 24,
                "Dendy 一帧应约 312×341 dots，实际 " + denDd);
        check(denDd == 3 * denDc, "Dendy 应为 1 CPU = 3 PPU dots，实际 cpu=" + denDc + " ppu=" + denDd);
        dendy.drainSamples();
        long denA0 = dendy.cpuCycles();
        dendy.stepFrame();
        int denExpect = (int) ((dendy.cpuCycles() - denA0) * 44100L / 1_773_447);
        check(Math.abs(dendy.drainSamples().length - denExpect) <= 1, "Dendy 采样应跟 1.773447 MHz 对齐");

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

        Console loopy = new Console(nromLoopy());
        for (int i = 0; i < 12; i++) {
            loopy.stepFrame();
        }
        int top = loopy.framebuffer()[50 * 256 + 128];
        int mid = loopy.framebuffer()[130 * 256 + 128];
        check(top == 0xFF000000, "loopy 上半应仍是 NT0 底色 $0F，实际 0x" + Integer.toHexString(top));
        check(mid == 0xFFB53120, "帧中途 $2006 后下半应是 NT2 实心 $16，实际 0x" + Integer.toHexString(mid));

        Console sprites = new Console(nromSprites());
        for (int i = 0; i < 8; i++) {
            sprites.stepFrame();
        }
        int sp8 = sprites.framebuffer()[51 * 256 + 4];
        int sp9 = sprites.framebuffer()[51 * 256 + 68];
        check(sprites.peekCpu(0) == 0xAA, "精灵测试盘应跑完初始化，$00=$AA 实际 $"
                + Integer.toHexString(sprites.peekCpu(0)));
        check(sp8 == 0xFFB53120, "第 8 个精灵应画出 $16，实际 0x" + Integer.toHexString(sp8));
        check(sp9 == 0xFF000000, "第 9 个精灵应被丢掉，实际 0x" + Integer.toHexString(sp9));
        check((sprites.peekCpu(0x2002) & 0x20) != 0, "9 个精灵同线应置溢出旗");

        Console overflowBug = new Console(nromOverflowBug());
        for (int i = 0; i < 8; i++) {
            overflowBug.stepFrame();
        }
        check(overflowBug.peekCpu(0) == 0xAA, "溢出 bug 盘应跑完初始化");
        check((overflowBug.peekCpu(0x2002) & 0x20) != 0, "n/m 错位后误读 tile 为 Y 也应置溢出");

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
            bad[6] = (byte) 0xC0;
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

        Cartridge cnrom = InesRom.load(cnromTwoChr());
        check(cnrom.ppuRead(0) == 0xAA, "CNROM 复位 CHR 应是第 0 页");
        cnrom.cpuWrite(0x8000, 1);
        check(cnrom.ppuRead(0) == 0xBB, "CNROM 切银行后 CHR 应是第 1 页");
        check(cnrom.cpuRead(0x8000) == 0x78, "CNROM 写银行不得改 PRG");

        Cartridge mmc3 = InesRom.load(mmc3Banks());
        check(mmc3.cpuRead(0x8000) == 0xAA, "MMC3 复位 $8000 应是 PRG 第 0 页");
        check(mmc3.cpuRead(0xC000) == 0xCC, "MMC3 复位 $C000 应固定倒数第二页");
        check(mmc3.cpuRead(0xE000) == 0xDD, "MMC3 $E000 应固定最后一页");
        mmc3.cpuWrite(0x8000, 6);
        mmc3.cpuWrite(0x8001, 1);
        check(mmc3.cpuRead(0x8000) == 0xBB, "MMC3 切 R6 后 $8000 应是第 1 页");
        check(mmc3.ppuRead(0) == 0xAA, "MMC3 复位 CHR $0000 应是 2K 第 0 页");
        mmc3.cpuWrite(0x8000, 0);
        mmc3.cpuWrite(0x8001, 2);
        check(mmc3.ppuRead(0) == 0xBB, "MMC3 切 R0=2 后 CHR $0000 应是 2K 第 2 页");
        mmc3.cpuWrite(0xC000, 0);
        mmc3.cpuWrite(0xC001, 0);
        mmc3.cpuWrite(0xE001, 0);
        check(!mmc3.irqAsserted(), "MMC3 未 clock 前不应 IRQ");
        mmc3.onPpuA12Rise();
        check(mmc3.irqAsserted(), "MMC3 latch=0 第一次 A12 应 IRQ");
        mmc3.cpuWrite(0xE000, 0);
        check(!mmc3.irqAsserted(), "MMC3 $E000 应应答并关掉 IRQ");
        mmc3.cpuWrite(0xC000, 0);
        mmc3.cpuWrite(0xC001, 0);
        mmc3.cpuWrite(0xE001, 0);
        for (int i = 0; i < 7; i++) {
            mmc3.onPpuA12(false);
        }
        mmc3.onPpuA12(true);
        check(!mmc3.irqAsserted(), "A12 低不足 8 dot 不应打拍");
        for (int i = 0; i < 8; i++) {
            mmc3.onPpuA12(false);
        }
        mmc3.onPpuA12(true);
        check(mmc3.irqAsserted(), "A12 低满 8 dot 后上升应打拍");

        Console mmc3Irq = new Console(mmc3IrqRom());
        for (int i = 0; i < 4; i++) {
            mmc3Irq.stepFrame();
        }
        check(mmc3Irq.peekCpu(0) != 0, "MMC3 渲染时应靠精灵表 A12 打出 IRQ");

        Cartridge mmc5 = InesRom.load(mmc5Banks());
        check(mmc5.cpuRead(0x8000) == 0xDD, "MMC5 复位 $8000 应是最后一页");
        mmc5.cpuWrite(0x5100, 3);
        mmc5.cpuWrite(0x5114, 0x80);
        check(mmc5.cpuRead(0x8000) == 0xAA, "MMC5 切 8K 后 $8000 应是第 0 页");
        mmc5.cpuWrite(0x5205, 4);
        mmc5.cpuWrite(0x5206, 5);
        check(mmc5.cpuRead(0x5205) == 20 && mmc5.cpuRead(0x5206) == 0, "MMC5 乘法 4×5 应为 20");
        mmc5.cpuWrite(0x5C00, 0x77);
        check(mmc5.cpuRead(0x5C00) == 0x77, "MMC5 ExRAM 应能读写");
        mmc5.cpuWrite(0x5203, 2);
        mmc5.cpuWrite(0x5204, 0x80);
        mmc5.onPpuScanline(2, true);
        check(mmc5.irqAsserted(), "MMC5 扫描线命中应 IRQ");
        mmc5.cpuWrite(0x5011, 40);
        check(mmc5.expansionPcm() == 40, "MMC5 PCM $5011 应出 40");
        mmc5.cpuWrite(0x5C00, 0x99);
        mmc5.cpuWrite(0x5200, 0x88);
        mmc5.onPpuScanline(0, true);
        mmc5.setPpuCoarseX(0);
        check(mmc5.nametableRead(0x2000) == 0x99, "MMC5 分割左侧应从 ExRAM 取");
        mmc5.setPpuCoarseX(16);
        check(mmc5.nametableRead(0x2000) < 0, "MMC5 分割右侧应回 CIRAM");
        mmc5.cpuWrite(0x5200, 0);
        mmc5.cpuWrite(0x5104, 1);
        mmc5.cpuWrite(0x5C00, 0xC0);
        mmc5.onPpuScanline(0, true);
        mmc5.setPpuCoarseX(0);
        check(mmc5.nametableRead(0x23C0) == 0xFF, "MMC5 8×8 属性 bit7-6=3 应铺成 $FF");

        Cartridge ffe16 = InesRom.load(ffe16Banks());
        check(ffe16.cpuRead(0x8000) == 0xAA, "FFE6 复位 $8000 应是第 0 页");
        ffe16.cpuWrite(0x8000, 0x05);
        check(ffe16.cpuRead(0x8000) == 0xBB, "FFE6 切 16K 后 $8000 应是第 1 页");
        check(ffe16.ppuRead(0) == 0xBB, "FFE6 切 CHR 后 $0000 应是第 1 页");
        ffe16.cpuWrite(0x4502, 0xFF);
        ffe16.cpuWrite(0x4503, 0xFF);
        check(!ffe16.irqAsserted(), "FFE $4503 装载后不应立即 IRQ");
        ffe16.clockCpu();
        check(ffe16.irqAsserted(), "FFE 计数从 $FFFF 溢出应 IRQ");
        ffe16.cpuWrite(0x4501, 0);
        check(!ffe16.irqAsserted(), "FFE $4501=0 应应答 IRQ");

        Cartridge ffe32 = InesRom.load(ffe32Banks());
        check(ffe32.cpuRead(0x8000) == 0xAA, "FFE8 复位 $8000 应是第 0 页");
        ffe32.cpuWrite(0x8000, 0x09);
        check(ffe32.cpuRead(0x8000) == 0xBB, "FFE8 切 32K 后 $8000 应是第 1 页");
        check(ffe32.ppuRead(0) == 0xBB, "FFE8 切 CHR 后 $0000 应是第 1 页");

        Cartridge gxrom = InesRom.load(gxromBanks());
        check(gxrom.cpuRead(0x8000) == 0xAA, "GxROM 复位 $8000 应是第 0 页");
        gxrom.cpuWrite(0x8000, 0x11);
        check(gxrom.cpuRead(0x8000) == 0xBB, "GxROM 切 PRG 后 $8000 应是第 1 页");
        check(gxrom.ppuRead(0) == 0xBB, "GxROM 切 CHR 后 $0000 应是第 1 页");

        Cartridge axrom = InesRom.load(axromBanks());
        check(axrom.cpuRead(0x8000) == 0xAA, "AxROM 复位 $8000 应是第 0 页");
        axrom.cpuWrite(0x8000, 1);
        check(axrom.cpuRead(0x8000) == 0xBB, "AxROM 切 32K 后 $8000 应是第 1 页");
        axrom.cpuWrite(0x8000, 0x10);
        check(axrom.nametableOffset(0x2000) == axrom.nametableOffset(0x2800), "AxROM bit4 应单屏");

        Cartridge mmc2 = InesRom.load(mmc2Banks());
        check(mmc2.cpuRead(0x8000) == 0xAA, "MMC2 复位 $8000 应是第 0 页");
        mmc2.cpuWrite(0xA000, 1);
        check(mmc2.cpuRead(0x8000) == 0xBB, "MMC2 切 8K 后 $8000 应是第 1 页");
        mmc2.cpuWrite(0xB000, 0);
        mmc2.cpuWrite(0xC000, 1);
        check(mmc2.ppuRead(0) == 0xBB, "MMC2 默认 FE 锁存应是 CHR 第 1 页");
        mmc2.ppuRead(0x0FD8);
        check(mmc2.ppuRead(0) == 0xAA, "MMC2 读 $0FD8 后应切到 FD 页");

        Cartridge mmc4 = InesRom.load(mmc4Banks());
        check(mmc4.cpuRead(0xC000) == 0xDD, "MMC4 $C000 应固定最后 16K");
        mmc4.cpuWrite(0xA000, 1);
        check(mmc4.cpuRead(0x8000) == 0xBB, "MMC4 切 16K 后 $8000 应是第 1 页");
        check(mmc4.cpuRead(0xC000) == 0xDD, "MMC4 切银行后 $C000 仍是最后一页");

        Cartridge dreams = InesRom.load(dreamsBanks());
        check(dreams.cpuRead(0x8000) == 0xAA, "Color Dreams 复位 $8000 应是第 0 页");
        dreams.cpuWrite(0x8000, 0x11);
        check(dreams.cpuRead(0x8000) == 0xBB, "Color Dreams 切 PRG 后 $8000 应是第 1 页");
        check(dreams.ppuRead(0) == 0xBB, "Color Dreams 切 CHR 后 $0000 应是第 1 页");

        Cartridge four = InesRom.load(nromFourScreen());
        four.nametableWrite(0x2000, 0xAA);
        four.nametableWrite(0x2C00, 0xBB);
        check(four.nametableRead(0x2000) == 0xAA, "four-screen NT0 应独立");
        check(four.nametableRead(0x2C00) == 0xBB, "four-screen NT3 应独立于 NT0");

        Cartridge cprom = InesRom.load(cpromBanks());
        cprom.ppuWrite(0x0000, 0xAA);
        cprom.cpuWrite(0x8000, 1);
        cprom.ppuWrite(0x1000, 0xBB);
        check(cprom.ppuRead(0x0000) == 0xAA, "CPROM $0000 应固定第 0 页");
        check(cprom.ppuRead(0x1000) == 0xBB, "CPROM 切银行后 $1000 应是第 1 页");
        cprom.cpuWrite(0x8000, 0);
        check(cprom.ppuRead(0x1000) == 0xAA, "CPROM 切回 0 后 $1000 应看到第 0 页");

        Cartridge bnrom = InesRom.load(bnromBanks());
        check(bnrom.cpuRead(0x8000) == 0xAA, "BNROM 复位 $8000 应是第 0 页");
        bnrom.cpuWrite(0x8000, 1);
        check(bnrom.cpuRead(0x8000) == 0xBB, "BNROM 切 32K 后 $8000 应是第 1 页");

        Cartridge nina001 = InesRom.load(nina001Banks());
        check(nina001.cpuRead(0x8000) == 0xAA, "NINA-001 复位 $8000 应是第 0 页");
        nina001.cpuWrite(0x7FFD, 1);
        nina001.cpuWrite(0x7FFE, 1);
        check(nina001.cpuRead(0x8000) == 0xBB, "NINA-001 $7FFD 应切 32K PRG");
        check(nina001.ppuRead(0) == 0xBB, "NINA-001 $7FFE 应切 $0000 的 4K CHR");

        Cartridge camerica = InesRom.load(camericaBanks());
        check(camerica.cpuRead(0x8000) == 0xAA, "Camerica 复位 $8000 应是第 0 页");
        check(camerica.cpuRead(0xC000) == 0xBB, "Camerica $C000 应固定最后一页");
        camerica.cpuWrite(0xC000, 1);
        check(camerica.cpuRead(0x8000) == 0xBB, "Camerica 写 $C000 后 $8000 应是第 1 页");
        camerica.cpuWrite(0x8000, 0x10);
        check(camerica.nametableOffset(0x2000) == camerica.nametableOffset(0x2800),
                "Camerica $8000 bit4 应单屏");

        Cartridge nina03 = InesRom.load(nina03Banks());
        check(nina03.cpuRead(0x8000) == 0xAA, "NINA-03 复位 $8000 应是第 0 页");
        nina03.cpuWrite(0x4100, 0x09);
        check(nina03.cpuRead(0x8000) == 0xBB, "NINA-03 bit3 应切 32K PRG");
        check(nina03.ppuRead(0) == 0xBB, "NINA-03 低 3 位应切 8K CHR");

        Cartridge jaleco87 = InesRom.load(jaleco87Banks());
        check(jaleco87.ppuRead(0) == 0xAA, "Jaleco 87 复位 CHR 应是第 0 页");
        jaleco87.cpuWrite(0x6000, 2);
        check(jaleco87.ppuRead(0) == 0xBB, "Jaleco 87 位对调后写 2 应切到第 1 页");

        Cartridge jaleco140 = InesRom.load(jaleco140Banks());
        check(jaleco140.cpuRead(0x8000) == 0xAA, "Jaleco 140 复位 $8000 应是第 0 页");
        jaleco140.cpuWrite(0x6000, 0x11);
        check(jaleco140.cpuRead(0x8000) == 0xBB, "Jaleco 140 高半字节应切 32K PRG");
        check(jaleco140.ppuRead(0) == 0xBB, "Jaleco 140 低半字节应切 8K CHR");

        Cartridge unrom180 = InesRom.load(unrom180Banks());
        check(unrom180.cpuRead(0x8000) == 0xAA, "UNROM-180 $8000 应固定第 0 页");
        check(unrom180.cpuRead(0xC000) == 0xAA, "UNROM-180 复位 $C000 应是第 0 页");
        unrom180.cpuWrite(0x8000, 1);
        check(unrom180.cpuRead(0xC000) == 0xBB, "UNROM-180 切银行后 $C000 应是第 1 页");
        check(unrom180.cpuRead(0x8000) == 0xAA, "UNROM-180 切银行后 $8000 仍是第 0 页");

        Cartridge dxrom = InesRom.load(dxromBanks());
        check(dxrom.cpuRead(0x8000) == 0xAA, "DxROM 复位 $8000 应是第 0 页");
        check(dxrom.cpuRead(0xC000) == 0xCC, "DxROM $C000 应固定倒数第二页");
        check(dxrom.cpuRead(0xE000) == 0xDD, "DxROM $E000 应固定最后一页");
        dxrom.cpuWrite(0x8000, 6);
        dxrom.cpuWrite(0x8001, 1);
        check(dxrom.cpuRead(0x8000) == 0xBB, "DxROM 切 R6 后 $8000 应是第 1 页");
        dxrom.cpuWrite(0x8000, 0);
        dxrom.cpuWrite(0x8001, 2);
        check(dxrom.ppuRead(0) == 0xBB, "DxROM 切 R0=2 后 CHR $0000 应是 2K 第 2 页");
        dxrom.onPpuA12Rise();
        check(!dxrom.irqAsserted(), "DxROM 不应有 MMC3 IRQ");

        Cartridge vrc1 = InesRom.load(vrc1Banks());
        check(vrc1.cpuRead(0x8000) == 0xAA, "VRC1 复位 $8000 应是第 0 页");
        vrc1.cpuWrite(0x8000, 1);
        check(vrc1.cpuRead(0x8000) == 0xBB, "VRC1 切 8K 后 $8000 应是第 1 页");
        vrc1.cpuWrite(0xE000, 1);
        check(vrc1.ppuRead(0) == 0xBB, "VRC1 切 4K CHR 后 $0000 应是第 1 页");

        Cartridge vrc4 = InesRom.load(vrc4Banks());
        check(vrc4.cpuRead(0x8000) == 0xAA, "VRC4 复位 $8000 应是第 0 页");
        vrc4.cpuWrite(0x8000, 1);
        check(vrc4.cpuRead(0x8000) == 0xBB, "VRC4 切 8K 后 $8000 应是第 1 页");
        vrc4.cpuWrite(0xB000, 2);
        check(vrc4.ppuRead(0) == 0xBB, "VRC4 切 1K CHR 后 $0000 应是第 2 页");
        vrc4.cpuWrite(0xF000, 0x0F);
        vrc4.cpuWrite(0xF001, 0x0F);
        vrc4.cpuWrite(0xF002, 0x06);
        check(!vrc4.irqAsserted(), "VRC4 装载后不应立即 IRQ");
        vrc4.clockCpu();
        check(vrc4.irqAsserted(), "VRC4 周期模式计数 $FF 应 IRQ");

        Cartridge vrc2 = InesRom.load(vrc2Banks());
        vrc2.cpuWrite(0xB000, 2);
        check(vrc2.ppuRead(0) == 0xBB, "VRC2a CHR 应右移 1（写 2 映到第 1 页）");
        vrc2.cpuWrite(0xF000, 0x0F);
        vrc2.cpuWrite(0xF001, 0x0F);
        vrc2.cpuWrite(0xF002, 0x06);
        vrc2.clockCpu();
        check(!vrc2.irqAsserted(), "VRC2 不应有 IRQ");

        Cartridge vrc3 = InesRom.load(vrc3Banks());
        check(vrc3.cpuRead(0xC000) == 0xBB, "VRC3 $C000 应固定最后 16K");
        vrc3.cpuWrite(0xF000, 1);
        check(vrc3.cpuRead(0x8000) == 0xBB, "VRC3 切 16K 后 $8000 应是第 1 页");
        vrc3.cpuWrite(0x8000, 0x0F);
        vrc3.cpuWrite(0x9000, 0x0F);
        vrc3.cpuWrite(0xA000, 0x0F);
        vrc3.cpuWrite(0xB000, 0x0F);
        vrc3.cpuWrite(0xC000, 2);
        vrc3.clockCpu();
        check(vrc3.irqAsserted(), "VRC3 16 位计数溢出应 IRQ");

        Cartridge vrc6 = InesRom.load(vrc6Banks());
        check(vrc6.cpuRead(0x8000) == 0xAA, "VRC6 复位 $8000 应是第 0 页");
        vrc6.cpuWrite(0x8000, 1);
        check(vrc6.cpuRead(0x8000) == 0xBB, "VRC6 切 16K 后 $8000 应是第 1 页");
        vrc6.cpuWrite(0xD000, 1);
        check(vrc6.ppuRead(0) == 0xBB, "VRC6 切 1K CHR 后 $0000 应是第 1 页");
        vrc6.cpuWrite(0x9000, 0x0F);
        vrc6.cpuWrite(0x9002, 0x80);
        check(vrc6.expansionPulse() == 15, "VRC6 方波应出音量 15");
        vrc6.cpuWrite(0xF000, 0xFF);
        vrc6.cpuWrite(0xF001, 0x06);
        vrc6.clockCpu();
        check(vrc6.irqAsserted(), "VRC6 周期 IRQ 应拉线");

        Cartridge vrc7 = InesRom.load(vrc7Banks());
        check(vrc7.cpuRead(0x8000) == 0xAA, "VRC7 复位 $8000 应是第 0 页");
        vrc7.cpuWrite(0x8000, 1);
        check(vrc7.cpuRead(0x8000) == 0xBB, "VRC7 切 8K 后 $8000 应是第 1 页");
        vrc7.cpuWrite(0x9010, 0x10);
        vrc7.cpuWrite(0x9030, 0x80);
        vrc7.cpuWrite(0x9010, 0x20);
        vrc7.cpuWrite(0x9030, 0x10);
        vrc7.cpuWrite(0x9010, 0x30);
        vrc7.cpuWrite(0x9030, 0x10);
        for (int i = 0; i < 4000; i++) {
            vrc7.clockCpu();
        }
        check(vrc7.expansionPcm() > 0, "VRC7 按键后应出扩展声");
        int vrc7Loud = vrc7.expansionPcm();
        vrc7.cpuWrite(0x9010, 0x20);
        vrc7.cpuWrite(0x9030, 0x00);
        for (int i = 0; i < 8000; i++) {
            vrc7.clockCpu();
        }
        check(vrc7.expansionPcm() < vrc7Loud, "VRC7 松键后包络应衰减");

        Ym2413 ym = new Ym2413(false);
        ym.write(0x30, 0x10);
        ym.write(0x10, 0x80);
        ym.write(0x20, 0x10);
        for (int i = 0; i < 4000; i++) {
            ym.tick(1_789_773);
        }
        check(ym.output() > 0, "YM2413 9 路旋律应按键出声");
        ym.write(0x16, 0x80);
        ym.write(0x26, 0x00);
        ym.write(0x0E, 0x30);
        for (int i = 0; i < 4000; i++) {
            ym.tick(1_789_773);
        }
        check(ym.output() > 0, "YM2413 节奏模式 BD 应出声");
        Ym2413 slots = new Ym2413(false);
        slots.write(0x30, 0x10);
        slots.write(0x10, 0x80);
        slots.write(0x20, 0x10);
        for (int i = 0; i < 72; i++) {
            slots.tick(3_579_545);
        }
        int held = slots.output();
        for (int i = 0; i < 71; i++) {
            slots.tick(3_579_545);
            check(slots.output() == held, "未满 72 chip clock 应保持上一样");
        }
        slots.tick(3_579_545);

        Cartridge fme7 = InesRom.load(fme7Banks());
        check(fme7.cpuRead(0x8000) == 0xAA, "FME-7 复位 $8000 应是第 0 页");
        fme7.cpuWrite(0x8000, 9);
        fme7.cpuWrite(0xA000, 1);
        check(fme7.cpuRead(0x8000) == 0xBB, "FME-7 命令 9 应切 $8000");
        fme7.cpuWrite(0x8000, 0);
        fme7.cpuWrite(0xA000, 1);
        check(fme7.ppuRead(0) == 0xBB, "FME-7 命令 0 应切 1K CHR");
        fme7.cpuWrite(0x8000, 14);
        fme7.cpuWrite(0xA000, 0);
        fme7.cpuWrite(0x8000, 15);
        fme7.cpuWrite(0xA000, 0);
        fme7.cpuWrite(0x8000, 13);
        fme7.cpuWrite(0xA000, 0x81);
        fme7.clockCpu();
        check(fme7.irqAsserted(), "FME-7 计数从 0 下溢应 IRQ");
        fme7.cpuWrite(0xC000, 0);
        fme7.cpuWrite(0xE000, 0xFF);
        fme7.cpuWrite(0xC000, 8);
        fme7.cpuWrite(0xE000, 0x0F);
        fme7.cpuWrite(0xC000, 7);
        fme7.cpuWrite(0xE000, 0x38);
        boolean heard5b = false;
        for (int i = 0; i < 4; i++) {
            if (fme7.expansionPulse() == 15) {
                heard5b = true;
                break;
            }
            fme7.clockCpu();
        }
        check(heard5b, "5B 方波 A 应出音量 15");

        Cartridge n163 = InesRom.load(n163Banks());
        check(n163.cpuRead(0x8000) == 0xAA, "Namco163 复位 $8000 应是第 0 页");
        n163.cpuWrite(0xE000, 1);
        check(n163.cpuRead(0x8000) == 0xBB, "Namco163 $E000 应切 8K PRG");
        n163.cpuWrite(0xC000, 1);
        check(n163.nametableRead(0x2000) == 0xBB, "Namco163 NT 可映到 CHR");
        n163.cpuWrite(0x5000, 0xFF);
        n163.cpuWrite(0x5800, 0xFF);
        n163.clockCpu();
        check(n163.irqAsserted(), "Namco163 计数到 $8000 应 IRQ");
        n163.cpuWrite(0xF800, 0x80);
        n163.cpuWrite(0x4800, 0x77);
        n163.cpuWrite(0xF800, 0x80);
        check(n163.cpuRead(0x4800) == 0x77, "Namco163 IRAM $4800 应能读写");

        Cartridge n175 = InesRom.load(namco175Banks());
        check(n175.cpuRead(0x8000) == 0xAA, "Namco175 复位 $8000 应是第 0 页");
        n175.cpuWrite(0xE000, 0xC1);
        check(n175.cpuRead(0x8000) == 0xBB, "Namco175 $E000 应切 8K PRG");
        int nt175 = n175.nametableOffset(0x2000);
        n175.cpuWrite(0xE000, 0x00);
        check(n175.nametableOffset(0x2000) == nt175, "Namco175 写 $E000 不得改镜像");

        Cartridge n340 = InesRom.load(namco340Banks());
        n340.cpuWrite(0xE000, 0x00);
        check(n340.nametableOffset(0x2000) == n340.nametableOffset(0x2400), "Namco340 bit6-7=0 应单屏 A");
        n340.cpuWrite(0xE000, 0x40);
        check(n340.nametableOffset(0x2000) != n340.nametableOffset(0x2400), "Namco340 垂直时 $2000≠$2400");
        check(n340.nametableOffset(0x2000) == n340.nametableOffset(0x2800), "Namco340 bit6-7=1 应垂直");
        n340.cpuWrite(0xE000, 1);
        check(n340.cpuRead(0x8000) == 0xBB, "Namco340 $E000 低位应切 PRG");

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

        Console split = new Console(nromBeep());
        split.drainSamples();
        for (int i = 0; i < 8; i++) {
            split.stepFrame();
        }
        short[] all = split.drainSamples();
        Console part = new Console(nromBeep());
        part.drainSamples();
        for (int i = 0; i < 8; i++) {
            part.stepFrame();
        }
        short[] head = new short[10];
        int took = part.drainSamples(head);
        short[] tail = part.drainSamples();
        check(took == 10 && took + tail.length == all.length, "drainTo 装不下应留下剩余采样");
        check(head[0] == all[0] && tail[0] == all[10], "留下的采样应接在已拷走的后面");

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

        Console unstable = new Console(nromUnstable());
        for (int i = 0; i < 4; i++) {
            unstable.stepFrame();
        }
        check(unstable.peekCpu(0x20) == 0x0E, "XAA 后 $20 应为 $0E，实际 $"
                + Integer.toHexString(unstable.peekCpu(0x20)));
        check(unstable.peekCpu(0x100) == 0x02, "SHX 后 $0100 应为 $02，实际 $"
                + Integer.toHexString(unstable.peekCpu(0x100)));
        check(unstable.peekCpu(0x101) == 0x02, "SHY 后 $0101 应为 $02，实际 $"
                + Integer.toHexString(unstable.peekCpu(0x101)));
        check(unstable.peekCpu(0x102) == 0x02, "SHA 后 $0102 应为 $02，实际 $"
                + Integer.toHexString(unstable.peekCpu(0x102)));
        check(unstable.peekCpu(0x21) == 0xF0 && unstable.peekCpu(0x22) == 0xF0
                        && unstable.peekCpu(0x23) == 0xF0,
                "LAS 后 A/X/S 应为 $F0");
        check(unstable.peekCpu(0x24) == 0x00, "XAA 应跟上一指令总线残留，实际 $"
                + Integer.toHexString(unstable.peekCpu(0x24)));

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

        int deathPeak = smbDeathPeak();
        check(deathPeak > 200, "玛丽死亡同款三角+$94 扫频应出声，峰值 " + deathPeak);

        System.out.println("selfcheck ok");
    }

    private static void mmc1Serial(Cartridge cart, int address, int value) {
        for (int i = 0; i < 5; i++) {
            cart.clockCpu();
            cart.cpuWrite(address, (value >> i) & 1);
        }
    }

    /** 超级玛丽死亡音：三角 $1F 一次性 + 方波 $94 扫频，每帧 $4017=$FF，并重触发。 */
    private static int smbDeathPeak() {
        nes.apu.Apu apu = new nes.apu.Apu();
        apu.reset();
        apu.write(0x4015, 0x0B);
        apu.write(0x4015, 0x0F);
        apu.write(0x4008, 0x1F);
        apu.write(0x400A, 0xB3);
        apu.write(0x400B, 0x08);
        apu.write(0x4000, 0x83);
        apu.write(0x4001, 0x94);
        apu.write(0x4002, 0xEF);
        apu.write(0x4003, 0x08);
        int peak = 0;
        for (int f = 0; f < 24; f++) {
            apu.write(0x4017, 0xFF);
            apu.write(0x4001, 0x94);
            if (f > 0 && f % 8 == 0) {
                apu.write(0x4008, 0x1F);
                apu.write(0x400B, 0x08);
                apu.write(0x4003, 0x08);
            }
            for (int i = 0; i < 29780; i++) {
                apu.tick();
            }
            for (short s : apu.drain()) {
                peak = Math.max(peak, Math.abs(s));
            }
        }
        return peak;
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    /**
     * CHR RAM。NT0 空，NT2 前 8 行实心。NMI 先把 t 拉回 $2000，烧到约扫描线 115 再写 $2006=$2800。
     * loopy：y=50 仍是底色，y=130 是实心。旧的 t+y 会把 y=130 算成 NT2 第 16 行（空）。
     */
    private static byte[] nromLoopy() {
        byte[] file = new byte[16 + 16384];
        file[0] = 'N';
        file[1] = 'E';
        file[2] = 'S';
        file[3] = 0x1A;
        file[4] = 1;
        file[5] = 0;
        byte[] code = {
            0x78, (byte) 0xD8, (byte) 0xA2, (byte) 0xFF, (byte) 0x9A,
            (byte) 0xA9, 0x00, (byte) 0x8D, 0x00, 0x20, (byte) 0x8D, 0x01, 0x20,
            0x2C, 0x02, 0x20, 0x10, (byte) 0xFB,
            0x2C, 0x02, 0x20, 0x10, (byte) 0xFB,
            (byte) 0xA9, 0x00, (byte) 0x8D, 0x06, 0x20,
            (byte) 0xA9, 0x10, (byte) 0x8D, 0x06, 0x20,
            (byte) 0xA2, 0x10, (byte) 0xA9, (byte) 0xFF,
            (byte) 0x8D, 0x07, 0x20, (byte) 0xCA, (byte) 0xD0, (byte) 0xFA,
            (byte) 0xA9, 0x28, (byte) 0x8D, 0x06, 0x20,
            (byte) 0xA9, 0x00, (byte) 0x8D, 0x06, 0x20,
            (byte) 0xA2, 0x00, (byte) 0xA9, 0x01,
            (byte) 0x8D, 0x07, 0x20, (byte) 0xCA, (byte) 0xD0, (byte) 0xFA,
            (byte) 0xA9, 0x3F, (byte) 0x8D, 0x06, 0x20,
            (byte) 0xA9, 0x00, (byte) 0x8D, 0x06, 0x20,
            (byte) 0xA9, 0x0F, (byte) 0x8D, 0x07, 0x20,
            (byte) 0xA9, 0x16, (byte) 0x8D, 0x07, 0x20,
            (byte) 0x8D, 0x07, 0x20, (byte) 0x8D, 0x07, 0x20,
            (byte) 0xA9, (byte) 0x80, (byte) 0x8D, 0x00, 0x20,
            (byte) 0xA9, 0x0A, (byte) 0x8D, 0x01, 0x20,
            0x4C, 0x63, (byte) 0x80,
            0x48, (byte) 0x8A, 0x48, (byte) 0x98, 0x48,
            (byte) 0xA9, 0x20, (byte) 0x8D, 0x06, 0x20,
            (byte) 0xA9, 0x00, (byte) 0x8D, 0x06, 0x20,
            (byte) 0xA2, 0x0C, (byte) 0xA0, 0x00,
            (byte) 0x88, (byte) 0xD0, (byte) 0xFD, (byte) 0xCA, (byte) 0xD0, (byte) 0xF8,
            (byte) 0xA9, 0x28, (byte) 0x8D, 0x06, 0x20,
            (byte) 0xA9, 0x00, (byte) 0x8D, 0x06, 0x20,
            0x68, (byte) 0xA8, 0x68, (byte) 0xAA, 0x68, 0x40
        };
        System.arraycopy(code, 0, file, 16, code.length);
        file[16 + 0x3FFA] = 0x66;
        file[16 + 0x3FFB] = (byte) 0x80;
        file[16 + 0x3FFC] = 0x00;
        file[16 + 0x3FFD] = (byte) 0x80;
        return file;
    }

    /** 与 nromRed 相同，flags9 标 PAL。 */
    private static byte[] nromPal() {
        byte[] file = nromRed();
        file[9] = 1;
        return file;
    }

    /** NES 2.0 byte12=3：Dendy。 */
    private static byte[] nromDendy() {
        byte[] file = nromRed();
        file[7] = 0x08;
        file[12] = 3;
        return file;
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

    /** 开渲染 + 精灵表 $1000，MMC3 latch=0，IRQ 写 $00。 */
    private static byte[] mmc3IrqRom() {
        byte[] file = new byte[16 + 32768];
        file[0] = 'N';
        file[1] = 'E';
        file[2] = 'S';
        file[3] = 0x1A;
        file[4] = 2;
        file[6] = 0x40;
        byte[] code = {
            0x78,
            (byte) 0xA9, 0x08, (byte) 0x8D, 0x00, 0x20,
            (byte) 0xA9, 0x18, (byte) 0x8D, 0x01, 0x20,
            (byte) 0xA9, 0x00, (byte) 0x8D, 0x00, (byte) 0xC0,
            (byte) 0x8D, 0x01, (byte) 0xC0, (byte) 0x8D, 0x01, (byte) 0xE0,
            (byte) 0x8D, 0x00, 0x00, 0x58,
            0x4C, 0x1A, (byte) 0xE0,
            (byte) 0xEE, 0x00, 0x00, (byte) 0x8D, 0x00, (byte) 0xE0, 0x40
        };
        System.arraycopy(code, 0, file, 16 + 24576, code.length);
        file[16 + 0x7FFC] = 0x00;
        file[16 + 0x7FFD] = (byte) 0xE0;
        file[16 + 0x7FFE] = 0x1D;
        file[16 + 0x7FFF] = (byte) 0xE0;
        return file;
    }

    /** 9 个同线精灵；第 9 个应丢掉并置溢出。 */
    private static byte[] nromSprites() {
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
            (byte) 0xA9, 0x00, (byte) 0x8D, 0x03, 0x20, (byte) 0x85, 0x10,
            (byte) 0xA2, 0x09,
            (byte) 0xA9, 0x32, (byte) 0x8D, 0x04, 0x20,
            (byte) 0xA9, 0x01, (byte) 0x8D, 0x04, 0x20,
            (byte) 0xA9, 0x00, (byte) 0x8D, 0x04, 0x20,
            (byte) 0xA5, 0x10, (byte) 0x8D, 0x04, 0x20,
            0x18, 0x69, 0x08, (byte) 0x85, 0x10,
            (byte) 0xCA, (byte) 0xD0, (byte) 0xE4,
            (byte) 0xA9, 0x3F, (byte) 0x8D, 0x06, 0x20,
            (byte) 0xA9, 0x00, (byte) 0x8D, 0x06, 0x20,
            (byte) 0xA9, 0x0F, (byte) 0x8D, 0x07, 0x20,
            (byte) 0xA9, 0x3F, (byte) 0x8D, 0x06, 0x20,
            (byte) 0xA9, 0x11, (byte) 0x8D, 0x06, 0x20,
            (byte) 0xA9, 0x16, (byte) 0x8D, 0x07, 0x20,
            (byte) 0xA9, 0x14, (byte) 0x8D, 0x01, 0x20,
            (byte) 0xA9, (byte) 0xAA, (byte) 0x85, 0x00,
            0x4C, 0x63, (byte) 0x80
        };
        System.arraycopy(code, 0, file, 16, code.length);
        int chr = 16 + 16384 + 16;
        for (int i = 0; i < 8; i++) {
            file[chr + i] = (byte) 0xFF;
        }
        file[16 + 0x3FFC] = 0x00;
        file[16 + 0x3FFD] = (byte) 0x80;
        return file;
    }

    /** XAA / SHX / SHY / SHA / LAS。 */
    private static byte[] nromUnstable() {
        byte[] file = new byte[16 + 16384 + 8192];
        file[0] = 'N';
        file[1] = 'E';
        file[2] = 'S';
        file[3] = 0x1A;
        file[4] = 1;
        file[5] = 1;
        byte[] code = {
            (byte) 0xA9, 0x00, (byte) 0xA2, 0x0F, (byte) 0x8B, (byte) 0xFF, (byte) 0x85, 0x20,
            (byte) 0xA2, (byte) 0xFF, (byte) 0xA0, 0x00, (byte) 0x9E, 0x00, 0x01,
            (byte) 0xA0, (byte) 0xC3, (byte) 0xA2, 0x00, (byte) 0x9C, 0x01, 0x01,
            (byte) 0xA9, (byte) 0xFF, (byte) 0xA2, (byte) 0xFF, (byte) 0xA0, 0x00, (byte) 0x9F, 0x02, 0x01,
            (byte) 0xA9, (byte) 0xF0, (byte) 0x85, 0x10, (byte) 0xA2, (byte) 0xFF, (byte) 0x9A,
            (byte) 0xA0, 0x00, (byte) 0xBB, 0x10, 0x00,
            (byte) 0x85, 0x21, (byte) 0x86, 0x22, (byte) 0xBA, (byte) 0x86, 0x23,
            (byte) 0xA2, (byte) 0xFF, (byte) 0xA9, 0x00, (byte) 0x8B, (byte) 0xFF, (byte) 0x85, 0x24,
            0x4C, 0x3A, (byte) 0x80
        };
        System.arraycopy(code, 0, file, 16, code.length);
        file[16 + 0x3FFC] = 0x00;
        file[16 + 0x3FFD] = (byte) 0x80;
        return file;
    }

    /** 8 个在线精灵 + 第 9 个 Y 不在线，tile=$32 被 n/m 错位当成 Y。 */
    private static byte[] nromOverflowBug() {
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
            (byte) 0xA9, 0x00, (byte) 0x8D, 0x03, 0x20, (byte) 0x85, 0x10,
            (byte) 0xA2, 0x08,
            (byte) 0xA9, 0x32, (byte) 0x8D, 0x04, 0x20,
            (byte) 0xA9, 0x01, (byte) 0x8D, 0x04, 0x20,
            (byte) 0xA9, 0x00, (byte) 0x8D, 0x04, 0x20,
            (byte) 0xA5, 0x10, (byte) 0x8D, 0x04, 0x20,
            0x18, 0x69, 0x08, (byte) 0x85, 0x10,
            (byte) 0xCA, (byte) 0xD0, (byte) 0xE4,
            (byte) 0xA9, (byte) 0xC8, (byte) 0x8D, 0x04, 0x20,
            (byte) 0xA9, 0x00, (byte) 0x8D, 0x04, 0x20,
            (byte) 0x8D, 0x04, 0x20, (byte) 0x8D, 0x04, 0x20,
            (byte) 0xA9, (byte) 0xC8, (byte) 0x8D, 0x04, 0x20,
            (byte) 0xA9, 0x32, (byte) 0x8D, 0x04, 0x20,
            (byte) 0xA9, 0x00, (byte) 0x8D, 0x04, 0x20,
            (byte) 0x8D, 0x04, 0x20,
            (byte) 0xA9, 0x3F, (byte) 0x8D, 0x06, 0x20,
            (byte) 0xA9, 0x00, (byte) 0x8D, 0x06, 0x20,
            (byte) 0xA9, 0x0F, (byte) 0x8D, 0x07, 0x20,
            (byte) 0xA9, 0x14, (byte) 0x8D, 0x01, 0x20,
            (byte) 0xA9, (byte) 0xAA, (byte) 0x85, 0x00,
            0x4C, 0x76, (byte) 0x80
        };
        System.arraycopy(code, 0, file, 16, code.length);
        file[16 + 0x3FFC] = 0x00;
        file[16 + 0x3FFD] = (byte) 0x80;
        return file;
    }

    /** 64K PRG、CHR RAM、mapper 7。 */
    private static byte[] axromBanks() {
        byte[] file = new byte[16 + 65536];
        file[0] = 'N';
        file[1] = 'E';
        file[2] = 'S';
        file[3] = 0x1A;
        file[4] = 4;
        file[6] = 0x70;
        file[16] = (byte) 0xAA;
        file[16 + 0x8000] = (byte) 0xBB;
        return file;
    }

    /** 32K PRG、16K CHR、mapper 9。 */
    private static byte[] mmc2Banks() {
        byte[] file = new byte[16 + 32768 + 16384];
        file[0] = 'N';
        file[1] = 'E';
        file[2] = 'S';
        file[3] = 0x1A;
        file[4] = 2;
        file[5] = 2;
        file[6] = (byte) 0x90;
        file[16] = (byte) 0xAA;
        file[16 + 0x2000] = (byte) 0xBB;
        file[16 + 32768] = (byte) 0xAA;
        file[16 + 32768 + 0x1000] = (byte) 0xBB;
        return file;
    }

    /** 64K PRG、CHR RAM、mapper 10。 */
    private static byte[] mmc4Banks() {
        byte[] file = new byte[16 + 65536];
        file[0] = 'N';
        file[1] = 'E';
        file[2] = 'S';
        file[3] = 0x1A;
        file[4] = 4;
        file[6] = (byte) 0xA0;
        file[16] = (byte) 0xAA;
        file[16 + 0x4000] = (byte) 0xBB;
        file[16 + 0xC000] = (byte) 0xDD;
        return file;
    }

    /** 64K PRG、16K CHR、mapper 11。 */
    private static byte[] dreamsBanks() {
        byte[] file = new byte[16 + 65536 + 16384];
        file[0] = 'N';
        file[1] = 'E';
        file[2] = 'S';
        file[3] = 0x1A;
        file[4] = 4;
        file[5] = 2;
        file[6] = (byte) 0xB0;
        file[16] = (byte) 0xAA;
        file[16 + 0x8000] = (byte) 0xBB;
        file[16 + 65536] = (byte) 0xAA;
        file[16 + 65536 + 0x2000] = (byte) 0xBB;
        return file;
    }

    /** 32K PRG、16K CHR、mapper 6。 */
    private static byte[] ffe16Banks() {
        byte[] file = new byte[16 + 32768 + 16384];
        file[0] = 'N';
        file[1] = 'E';
        file[2] = 'S';
        file[3] = 0x1A;
        file[4] = 2;
        file[5] = 2;
        file[6] = 0x60;
        file[16] = (byte) 0xAA;
        file[16 + 0x4000] = (byte) 0xBB;
        file[16 + 32768] = (byte) 0xAA;
        file[16 + 32768 + 8192] = (byte) 0xBB;
        return file;
    }

    /** 64K PRG、16K CHR、mapper 8。 */
    private static byte[] ffe32Banks() {
        byte[] file = new byte[16 + 65536 + 16384];
        file[0] = 'N';
        file[1] = 'E';
        file[2] = 'S';
        file[3] = 0x1A;
        file[4] = 4;
        file[5] = 2;
        file[6] = (byte) 0x80;
        file[16] = (byte) 0xAA;
        file[16 + 0x8000] = (byte) 0xBB;
        file[16 + 65536] = (byte) 0xAA;
        file[16 + 65536 + 8192] = (byte) 0xBB;
        return file;
    }

    /** 64K PRG、16K CHR、mapper 66。 */
    private static byte[] gxromBanks() {
        byte[] file = new byte[16 + 65536 + 16384];
        file[0] = 'N';
        file[1] = 'E';
        file[2] = 'S';
        file[3] = 0x1A;
        file[4] = 4;
        file[5] = 2;
        file[6] = 0x20;
        file[7] = 0x40;
        file[16] = (byte) 0xAA;
        file[16 + 0x8000] = (byte) 0xBB;
        file[16 + 65536] = (byte) 0xAA;
        file[16 + 65536 + 8192] = (byte) 0xBB;
        return file;
    }

    /** 32K PRG（4×8K）、8K CHR、mapper 5。 */
    private static byte[] mmc5Banks() {
        byte[] file = new byte[16 + 32768 + 8192];
        file[0] = 'N';
        file[1] = 'E';
        file[2] = 'S';
        file[3] = 0x1A;
        file[4] = 2;
        file[5] = 1;
        file[6] = 0x50;
        file[16] = (byte) 0xAA;
        file[16 + 0x2000] = (byte) 0xBB;
        file[16 + 0x4000] = (byte) 0xCC;
        file[16 + 0x6000] = (byte) 0xDD;
        return file;
    }

    /** 32K PRG（4×8K）、16K CHR、mapper 4。 */
    private static byte[] mmc3Banks() {
        byte[] file = new byte[16 + 32768 + 16384];
        file[0] = 'N';
        file[1] = 'E';
        file[2] = 'S';
        file[3] = 0x1A;
        file[4] = 2;
        file[5] = 2;
        file[6] = 0x40;
        file[16] = (byte) 0xAA;
        file[16 + 0x2000] = (byte) 0xBB;
        file[16 + 0x4000] = (byte) 0xCC;
        file[16 + 0x6000] = (byte) 0xDD;
        file[16 + 32768] = (byte) 0xAA;
        file[16 + 32768 + 0x800] = (byte) 0xBB;
        return file;
    }

    /** 16K PRG、两页 8K CHR、mapper 3：第 0 页 $AA，第 1 页 $BB。 */
    private static byte[] cnromTwoChr() {
        byte[] file = new byte[16 + 16384 + 16384];
        file[0] = 'N';
        file[1] = 'E';
        file[2] = 'S';
        file[3] = 0x1A;
        file[4] = 1;
        file[5] = 2;
        file[6] = 0x30;
        file[16] = 0x78;
        file[16 + 16384] = (byte) 0xAA;
        file[16 + 16384 + 8192] = (byte) 0xBB;
        return file;
    }

    /** 32K PRG、16K CHR、mapper 75。 */
    private static byte[] vrc1Banks() {
        byte[] file = mapperFile(75, 2, 2);
        file[16] = (byte) 0xAA;
        file[16 + 0x2000] = (byte) 0xBB;
        file[16 + 32768] = (byte) 0xAA;
        file[16 + 32768 + 0x1000] = (byte) 0xBB;
        return file;
    }

    /** 32K PRG、16K CHR、mapper 21。 */
    private static byte[] vrc4Banks() {
        byte[] file = mapperFile(21, 2, 2);
        file[16] = (byte) 0xAA;
        file[16 + 0x2000] = (byte) 0xBB;
        file[16 + 32768] = (byte) 0xAA;
        file[16 + 32768 + 0x800] = (byte) 0xBB;
        return file;
    }

    /** 16K PRG、16K CHR、mapper 22。 */
    private static byte[] vrc2Banks() {
        byte[] file = mapperFile(22, 1, 2);
        file[16 + 16384] = (byte) 0xAA;
        file[16 + 16384 + 0x400] = (byte) 0xBB;
        return file;
    }

    /** 32K PRG、CHR RAM、mapper 73。 */
    private static byte[] vrc3Banks() {
        byte[] file = mapperFile(73, 2, 0);
        file[16] = (byte) 0xAA;
        file[16 + 0x4000] = (byte) 0xBB;
        return file;
    }

    /** 32K PRG、16K CHR、mapper 24。 */
    private static byte[] vrc6Banks() {
        byte[] file = mapperFile(24, 2, 2);
        file[16] = (byte) 0xAA;
        file[16 + 0x4000] = (byte) 0xBB;
        file[16 + 32768] = (byte) 0xAA;
        file[16 + 32768 + 0x400] = (byte) 0xBB;
        return file;
    }

    /** 32K PRG、CHR RAM、mapper 85。 */
    private static byte[] vrc7Banks() {
        byte[] file = mapperFile(85, 2, 0);
        file[16] = (byte) 0xAA;
        file[16 + 0x2000] = (byte) 0xBB;
        return file;
    }

    /** 32K PRG、16K CHR、mapper 69。 */
    private static byte[] fme7Banks() {
        byte[] file = mapperFile(69, 2, 2);
        file[16] = (byte) 0xAA;
        file[16 + 0x2000] = (byte) 0xBB;
        file[16 + 32768] = (byte) 0xAA;
        file[16 + 32768 + 0x400] = (byte) 0xBB;
        return file;
    }

    /** 32K PRG、16K CHR、mapper 19。 */
    private static byte[] n163Banks() {
        byte[] file = mapperFile(19, 2, 2);
        file[16] = (byte) 0xAA;
        file[16 + 0x2000] = (byte) 0xBB;
        file[16 + 32768] = (byte) 0xAA;
        file[16 + 32768 + 0x400] = (byte) 0xBB;
        return file;
    }

    /** 32K PRG、16K CHR、mapper 210 NES2 子 1（175）。 */
    private static byte[] namco175Banks() {
        byte[] file = mapperFile(210, 2, 2);
        file[7] = (byte) 0xD8;
        file[8] = 0x10;
        file[16] = (byte) 0xAA;
        file[16 + 0x2000] = (byte) 0xBB;
        return file;
    }

    /** 32K PRG、16K CHR、mapper 210（340）。 */
    private static byte[] namco340Banks() {
        byte[] file = mapperFile(210, 2, 2);
        file[16] = (byte) 0xAA;
        file[16 + 0x2000] = (byte) 0xBB;
        return file;
    }

    private static byte[] mapperFile(int mapper, int prg16k, int chr8k) {
        byte[] file = new byte[16 + prg16k * 16384 + chr8k * 8192];
        file[0] = 'N';
        file[1] = 'E';
        file[2] = 'S';
        file[3] = 0x1A;
        file[4] = (byte) prg16k;
        file[5] = (byte) chr8k;
        file[6] = (byte) ((mapper & 0x0F) << 4);
        file[7] = (byte) (mapper & 0xF0);
        return file;
    }

    /** 16K PRG、NROM、four-screen。 */
    private static byte[] nromFourScreen() {
        byte[] file = new byte[16 + 16384 + 8192];
        file[0] = 'N';
        file[1] = 'E';
        file[2] = 'S';
        file[3] = 0x1A;
        file[4] = 1;
        file[5] = 1;
        file[6] = 0x08;
        return file;
    }

    /** 32K PRG、CHR RAM 16K、mapper 13。 */
    private static byte[] cpromBanks() {
        byte[] file = new byte[16 + 32768];
        file[0] = 'N';
        file[1] = 'E';
        file[2] = 'S';
        file[3] = 0x1A;
        file[4] = 2;
        file[6] = (byte) 0xD0;
        return file;
    }

    /** 64K PRG、CHR RAM、mapper 34（BNROM）。 */
    private static byte[] bnromBanks() {
        byte[] file = new byte[16 + 65536];
        file[0] = 'N';
        file[1] = 'E';
        file[2] = 'S';
        file[3] = 0x1A;
        file[4] = 4;
        file[6] = 0x20;
        file[7] = 0x20;
        file[16] = (byte) 0xAA;
        file[16 + 0x8000] = (byte) 0xBB;
        return file;
    }

    /** 64K PRG、16K CHR、mapper 34（NINA-001）。 */
    private static byte[] nina001Banks() {
        byte[] file = new byte[16 + 65536 + 16384];
        file[0] = 'N';
        file[1] = 'E';
        file[2] = 'S';
        file[3] = 0x1A;
        file[4] = 4;
        file[5] = 2;
        file[6] = 0x20;
        file[7] = 0x20;
        file[16] = (byte) 0xAA;
        file[16 + 0x8000] = (byte) 0xBB;
        file[16 + 65536] = (byte) 0xAA;
        file[16 + 65536 + 0x1000] = (byte) 0xBB;
        return file;
    }

    /** 32K PRG、CHR RAM、mapper 71。 */
    private static byte[] camericaBanks() {
        byte[] file = new byte[16 + 32768];
        file[0] = 'N';
        file[1] = 'E';
        file[2] = 'S';
        file[3] = 0x1A;
        file[4] = 2;
        file[6] = 0x70;
        file[7] = 0x40;
        file[16] = (byte) 0xAA;
        file[16 + 0x4000] = (byte) 0xBB;
        return file;
    }

    /** 64K PRG、16K CHR、mapper 79。 */
    private static byte[] nina03Banks() {
        byte[] file = new byte[16 + 65536 + 16384];
        file[0] = 'N';
        file[1] = 'E';
        file[2] = 'S';
        file[3] = 0x1A;
        file[4] = 4;
        file[5] = 2;
        file[6] = (byte) 0xF0;
        file[7] = 0x40;
        file[16] = (byte) 0xAA;
        file[16 + 0x8000] = (byte) 0xBB;
        file[16 + 65536] = (byte) 0xAA;
        file[16 + 65536 + 0x2000] = (byte) 0xBB;
        return file;
    }

    /** 16K PRG、16K CHR、mapper 87。 */
    private static byte[] jaleco87Banks() {
        byte[] file = new byte[16 + 16384 + 16384];
        file[0] = 'N';
        file[1] = 'E';
        file[2] = 'S';
        file[3] = 0x1A;
        file[4] = 1;
        file[5] = 2;
        file[6] = 0x70;
        file[7] = 0x50;
        file[16 + 16384] = (byte) 0xAA;
        file[16 + 16384 + 8192] = (byte) 0xBB;
        return file;
    }

    /** 64K PRG、16K CHR、mapper 140。 */
    private static byte[] jaleco140Banks() {
        byte[] file = new byte[16 + 65536 + 16384];
        file[0] = 'N';
        file[1] = 'E';
        file[2] = 'S';
        file[3] = 0x1A;
        file[4] = 4;
        file[5] = 2;
        file[6] = (byte) 0xC0;
        file[7] = (byte) 0x80;
        file[16] = (byte) 0xAA;
        file[16 + 0x8000] = (byte) 0xBB;
        file[16 + 65536] = (byte) 0xAA;
        file[16 + 65536 + 8192] = (byte) 0xBB;
        return file;
    }

    /** 32K PRG、CHR RAM、mapper 180。 */
    private static byte[] unrom180Banks() {
        byte[] file = new byte[16 + 32768];
        file[0] = 'N';
        file[1] = 'E';
        file[2] = 'S';
        file[3] = 0x1A;
        file[4] = 2;
        file[6] = 0x40;
        file[7] = (byte) 0xB0;
        file[16] = (byte) 0xAA;
        file[16 + 0x4000] = (byte) 0xBB;
        return file;
    }

    /** 32K PRG（4×8K）、16K CHR、mapper 206。 */
    private static byte[] dxromBanks() {
        byte[] file = new byte[16 + 32768 + 16384];
        file[0] = 'N';
        file[1] = 'E';
        file[2] = 'S';
        file[3] = 0x1A;
        file[4] = 2;
        file[5] = 2;
        file[6] = (byte) 0xE0;
        file[7] = (byte) 0xC0;
        file[16] = (byte) 0xAA;
        file[16 + 0x2000] = (byte) 0xBB;
        file[16 + 0x4000] = (byte) 0xCC;
        file[16 + 0x6000] = (byte) 0xDD;
        file[16 + 32768] = (byte) 0xAA;
        file[16 + 32768 + 0x800] = (byte) 0xBB;
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
