package nes.cart;

/**
 * Mapper 5（MMC5）。PRG/CHR、ExRAM/fill、8×8 属性、乘法、扫描线 IRQ、扩展方波/PCM、分割卷轴。
 * ponytail: 8×8 属性用当前 coarse X/扫描线，不是 AT 地址解码。天花板：属性测试盘。升级：按 tile 索引。
 */
final class Mmc5 implements Cartridge {
    private final byte[] prg;
    private final byte[] chr;
    private final byte[] prgRam = new byte[0x8000];
    private final byte[] exram = new byte[0x400];
    private final boolean chrRam;
    private final int[] prgBank = new int[4];
    private final int[] chrSpr = new int[8];
    private final int[] chrBg = new int[4];
    private int prgMode = 3;
    private int chrMode = 3;
    private int exMode;
    private int prgRamBank;
    private int ntMap;
    private int fillTile;
    private int fillAttr;
    private int chrHi;
    private int mul0;
    private int mul1;
    private int irqTarget;
    private boolean irqEnable;
    private boolean irqPending;
    private boolean inFrame;
    private boolean spriteChr;
    private int coarseX;
    private int sl;
    private int splitCtrl;
    private int splitY;
    private int splitChr;
    private int pcm;
    private int frame;
    private final ExpPulse pulse1 = new ExpPulse();
    private final ExpPulse pulse2 = new ExpPulse();

    Mmc5(byte[] prg, byte[] chr, boolean chrRam) {
        this.prg = prg;
        this.chr = chr;
        this.chrRam = chrRam;
        int last = Math.max(0, prg.length / 0x2000 - 1) | 0x80;
        prgBank[0] = last;
        prgBank[1] = last;
        prgBank[2] = last;
        prgBank[3] = last;
    }

    @Override
    public int cpuRead(int address) {
        address &= 0xFFFF;
        if (address >= 0x8000) {
            return prg[prgOffset(address)] & 0xFF;
        }
        if (address >= 0x6000) {
            int off = (prgRamBank * 0x2000 + (address - 0x6000)) & (prgRam.length - 1);
            return prgRam[off] & 0xFF;
        }
        if (address >= 0x5C00) {
            return exram[address & 0x3FF] & 0xFF;
        }
        if (address == 0x5204) {
            int v = (irqPending ? 0x80 : 0) | (inFrame ? 0x40 : 0);
            irqPending = false;
            return v;
        }
        if (address == 0x5205) {
            return (mul0 * mul1) & 0xFF;
        }
        if (address == 0x5206) {
            return ((mul0 * mul1) >> 8) & 0xFF;
        }
        return 0;
    }

    @Override
    public void cpuWrite(int address, int value) {
        address &= 0xFFFF;
        value &= 0xFF;
        if (address >= 0x8000) {
            return;
        }
        if (address >= 0x6000) {
            int off = (prgRamBank * 0x2000 + (address - 0x6000)) & (prgRam.length - 1);
            prgRam[off] = (byte) value;
            return;
        }
        if (address >= 0x5C00) {
            exram[address & 0x3FF] = (byte) value;
            return;
        }
        switch (address) {
            case 0x5100 -> prgMode = value & 3;
            case 0x5101 -> chrMode = value & 3;
            case 0x5104 -> exMode = value & 3;
            case 0x5105 -> ntMap = value;
            case 0x5106 -> fillTile = value;
            case 0x5107 -> fillAttr = value;
            case 0x5113 -> prgRamBank = value & 3;
            case 0x5114 -> prgBank[0] = value;
            case 0x5115 -> prgBank[1] = value;
            case 0x5116 -> prgBank[2] = value;
            case 0x5117 -> prgBank[3] = value;
            case 0x5120, 0x5121, 0x5122, 0x5123, 0x5124, 0x5125, 0x5126, 0x5127 ->
                    chrSpr[address - 0x5120] = value;
            case 0x5128, 0x5129, 0x512A, 0x512B -> chrBg[address - 0x5128] = value;
            case 0x5130 -> chrHi = value & 3;
            case 0x5000 -> pulse1.write0(value);
            case 0x5002 -> pulse1.write2(value);
            case 0x5003 -> pulse1.write3(value);
            case 0x5004 -> pulse2.write0(value);
            case 0x5006 -> pulse2.write2(value);
            case 0x5007 -> pulse2.write3(value);
            case 0x5011 -> pcm = value & 0x7F;
            case 0x5015 -> {
                pulse1.setEnabled((value & 1) != 0);
                pulse2.setEnabled((value & 2) != 0);
            }
            case 0x5200 -> splitCtrl = value;
            case 0x5201 -> splitY = value;
            case 0x5202 -> splitChr = value;
            case 0x5203 -> irqTarget = value;
            case 0x5204 -> irqEnable = (value & 0x80) != 0;
            case 0x5205 -> mul0 = value;
            case 0x5206 -> mul1 = value;
            default -> {
            }
        }
    }

    @Override
    public int ppuRead(int address) {
        return chr[chrOffset(address)] & 0xFF;
    }

    @Override
    public void ppuWrite(int address, int value) {
        if (chrRam) {
            chr[chrOffset(address)] = (byte) value;
        }
    }

    @Override
    public int nametableOffset(int address) {
        int src = ntSource(address);
        if (src == 1) {
            return 0x400 | (address & 0x3FF);
        }
        return address & 0x3FF;
    }

    @Override
    public int nametableRead(int address) {
        if (splitOn()) {
            int y = (sl + splitY) % 240;
            int tile = (y >> 3) * 32 + coarseX;
            int off = address & 0x3FF;
            if (off >= 0x3C0) {
                return exram[0x3C0 + (y >> 5) * 8 + (coarseX >> 2)] & 0xFF;
            }
            return exram[tile & 0x3FF] & 0xFF;
        }
        int off = address & 0x3FF;
        if (exMode == 1 && off >= 0x3C0) {
            int i = ((sl % 240) >> 3) * 32 + coarseX;
            return ((exram[i & 0x3FF] >> 6) & 3) * 0x55;
        }
        int src = ntSource(address);
        if (src == 2) {
            return exram[off] & 0xFF;
        }
        if (src == 3) {
            return off >= 0x3C0 ? fillAttr : fillTile;
        }
        return -1;
    }

    @Override
    public boolean nametableWrite(int address, int value) {
        int src = ntSource(address);
        if (src == 2) {
            exram[address & 0x3FF] = (byte) value;
            return true;
        }
        return src == 3;
    }

    @Override
    public void onPpuScanline(int scanline, boolean rendering) {
        if (scanline >= 240 && scanline != 261 && scanline != 311) {
            inFrame = false;
            return;
        }
        if (!rendering) {
            inFrame = false;
            return;
        }
        if (scanline == 261 || scanline == 311) {
            inFrame = true;
            return;
        }
        inFrame = true;
        sl = scanline;
        if (irqEnable && scanline == irqTarget) {
            irqPending = true;
        }
    }

    @Override
    public void setPpuCoarseX(int x) {
        coarseX = x & 31;
    }

    @Override
    public void clockCpu() {
        pulse1.clockTimer();
        pulse2.clockTimer();
        frame++;
        if (frame == 7457 || frame == 14913 || frame == 22371 || frame == 29829) {
            pulse1.quarter();
            pulse2.quarter();
            if (frame == 14913 || frame == 29829) {
                pulse1.half();
                pulse2.half();
            }
        }
        if (frame >= 29830) {
            frame = 0;
        }
    }

    @Override
    public int expansionPulse() {
        return pulse1.output() + pulse2.output();
    }

    @Override
    public int expansionPcm() {
        return pcm;
    }

    @Override
    public void setChrSpriteWindow(boolean sprite) {
        spriteChr = sprite;
    }

    @Override
    public boolean irqAsserted() {
        return irqPending && irqEnable;
    }

    @Override
    public void saveState(java.io.DataOutput out) throws java.io.IOException {
        out.writeInt(5);
        out.writeInt(prg.length);
        out.writeInt(chr.length);
        out.writeBoolean(chrRam);
        out.write(prgRam);
        out.write(exram);
        out.writeInt(prgMode);
        out.writeInt(chrMode);
        out.writeInt(exMode);
        out.writeInt(prgRamBank);
        out.writeInt(ntMap);
        out.writeInt(fillTile);
        out.writeInt(fillAttr);
        out.writeInt(chrHi);
        for (int b : prgBank) {
            out.writeInt(b);
        }
        for (int b : chrSpr) {
            out.writeInt(b);
        }
        for (int b : chrBg) {
            out.writeInt(b);
        }
        out.writeInt(mul0);
        out.writeInt(mul1);
        out.writeInt(irqTarget);
        out.writeBoolean(irqEnable);
        out.writeBoolean(irqPending);
        out.writeBoolean(inFrame);
        out.writeBoolean(spriteChr);
        out.writeInt(splitCtrl);
        out.writeInt(splitY);
        out.writeInt(splitChr);
        out.writeInt(pcm);
        pulse1.save(out);
        pulse2.save(out);
    }

    @Override
    public void loadState(java.io.DataInput in) throws java.io.IOException {
        if (in.readInt() != 5) {
            throw new java.io.IOException("存档不是 MMC5");
        }
        if (in.readInt() != prg.length || in.readInt() != chr.length || in.readBoolean() != chrRam) {
            throw new java.io.IOException("存档与当前盘不匹配");
        }
        in.readFully(prgRam);
        in.readFully(exram);
        prgMode = in.readInt();
        chrMode = in.readInt();
        exMode = in.readInt();
        prgRamBank = in.readInt();
        ntMap = in.readInt();
        fillTile = in.readInt();
        fillAttr = in.readInt();
        chrHi = in.readInt();
        for (int i = 0; i < prgBank.length; i++) {
            prgBank[i] = in.readInt();
        }
        for (int i = 0; i < chrSpr.length; i++) {
            chrSpr[i] = in.readInt();
        }
        for (int i = 0; i < chrBg.length; i++) {
            chrBg[i] = in.readInt();
        }
        mul0 = in.readInt();
        mul1 = in.readInt();
        irqTarget = in.readInt();
        irqEnable = in.readBoolean();
        irqPending = in.readBoolean();
        inFrame = in.readBoolean();
        spriteChr = in.readBoolean();
        splitCtrl = in.readInt();
        splitY = in.readInt();
        splitChr = in.readInt();
        pcm = in.readInt();
        pulse1.load(in);
        pulse2.load(in);
    }

    private int ntSource(int address) {
        int nt = (address >> 10) & 3;
        return (ntMap >> (nt * 2)) & 3;
    }

    private int prgOffset(int address) {
        int banks = Math.max(1, prg.length / 0x2000);
        int slot;
        int mode = prgMode & 3;
        if (mode == 0) {
            int base = (prgBank[3] & 0x7F) >> 2;
            slot = base * 4 + ((address >> 13) & 3);
        } else if (mode == 1) {
            int reg = address < 0xC000 ? prgBank[1] : prgBank[3];
            slot = ((reg & 0x7F) >> 1) * 2 + ((address >> 13) & 1);
        } else if (mode == 2) {
            if (address < 0xC000) {
                slot = ((prgBank[1] & 0x7F) >> 1) * 2 + ((address >> 13) & 1);
            } else if (address < 0xE000) {
                slot = prgBank[2] & 0x7F;
            } else {
                slot = prgBank[3] & 0x7F;
            }
        } else if (address < 0xA000) {
            slot = prgBank[0] & 0x7F;
        } else if (address < 0xC000) {
            slot = prgBank[1] & 0x7F;
        } else if (address < 0xE000) {
            slot = prgBank[2] & 0x7F;
        } else {
            slot = prgBank[3] & 0x7F;
        }
        return Math.floorMod(slot, banks) * 0x2000 + (address & 0x1FFF);
    }

    private boolean splitOn() {
        if ((splitCtrl & 0x80) == 0) {
            return false;
        }
        int tile = splitCtrl & 0x1F;
        return (splitCtrl & 0x40) != 0 ? coarseX >= tile : coarseX < tile;
    }

    private int chrOffset(int address) {
        int a = address & 0x1FFF;
        if (!spriteChr && splitOn()) {
            return Math.floorMod(splitChr * 0x1000 + (a & 0x0FFF), chr.length);
        }
        int mode = chrMode & 3;
        int bank;
        if (mode == 0) {
            int r = spriteChr ? chrSpr[7] : chrBg[3];
            bank = r * 8 + (a >> 10);
        } else if (mode == 1) {
            int r = spriteChr ? chrSpr[a >= 0x1000 ? 7 : 3] : chrBg[3];
            bank = r * 4 + ((a >> 10) & 3);
        } else if (mode == 2) {
            int i = (a >> 11) & 3;
            int r = spriteChr ? chrSpr[i * 2 + 1] : chrBg[Math.min(3, i)];
            bank = r * 2 + ((a >> 10) & 1);
        } else {
            bank = spriteChr ? chrSpr[(a >> 10) & 7] : chrBg[(a >> 10) & 3];
        }
        bank |= chrHi << 8;
        return Math.floorMod(bank * 0x400 + (a & 0x3FF), chr.length);
    }

    private static final int[] LENGTH = {
        10, 254, 20, 2, 40, 4, 80, 6, 160, 8, 60, 10, 14, 12, 26, 14,
        12, 16, 24, 18, 48, 20, 96, 22, 192, 24, 72, 26, 16, 28, 32, 30
    };
    private static final int[][] DUTY = {
        {0, 0, 0, 0, 0, 0, 0, 1},
        {0, 0, 0, 0, 0, 0, 1, 1},
        {0, 0, 0, 0, 1, 1, 1, 1},
        {1, 1, 1, 1, 1, 1, 0, 0}
    };

    private static final class ExpPulse {
        boolean enabled;
        boolean halt;
        boolean constant;
        boolean start;
        int duty;
        int dutyPos;
        int volume;
        int period;
        int timer;
        int length;
        int decay = 15;
        int divider;

        void write0(int value) {
            duty = (value >> 6) & 3;
            halt = (value & 0x20) != 0;
            constant = (value & 0x10) != 0;
            volume = value & 0x0F;
        }

        void write2(int value) {
            period = (period & 0x700) | value;
        }

        void write3(int value) {
            period = (period & 0xFF) | ((value & 7) << 8);
            if (enabled) {
                length = LENGTH[(value >> 3) & 0x1F];
            }
            dutyPos = 0;
            start = true;
            timer = period;
        }

        void setEnabled(boolean on) {
            enabled = on;
            if (!on) {
                length = 0;
            }
        }

        void clockTimer() {
            if (timer == 0) {
                timer = period;
                dutyPos = (dutyPos + 1) & 7;
            } else {
                timer--;
            }
        }

        void quarter() {
            if (start) {
                decay = 15;
                divider = volume;
                start = false;
                return;
            }
            if (divider == 0) {
                divider = volume;
                if (decay > 0) {
                    decay--;
                } else if (halt) {
                    decay = 15;
                }
            } else {
                divider--;
            }
        }

        void half() {
            if (!halt && length > 0) {
                length--;
            }
        }

        int output() {
            if (!enabled || length == 0 || period < 8 || DUTY[duty][dutyPos] == 0) {
                return 0;
            }
            return constant ? volume : decay;
        }

        void save(java.io.DataOutput out) throws java.io.IOException {
            out.writeBoolean(enabled);
            out.writeBoolean(halt);
            out.writeBoolean(constant);
            out.writeBoolean(start);
            out.writeInt(duty);
            out.writeInt(dutyPos);
            out.writeInt(volume);
            out.writeInt(period);
            out.writeInt(timer);
            out.writeInt(length);
            out.writeInt(decay);
            out.writeInt(divider);
        }

        void load(java.io.DataInput in) throws java.io.IOException {
            enabled = in.readBoolean();
            halt = in.readBoolean();
            constant = in.readBoolean();
            start = in.readBoolean();
            duty = in.readInt();
            dutyPos = in.readInt();
            volume = in.readInt();
            period = in.readInt();
            timer = in.readInt();
            length = in.readInt();
            decay = in.readInt();
            divider = in.readInt();
        }
    }
}
