package nes.cart;

/**
 * Mapper 69（FME-7 / 5B）。命令口切 8K PRG/1K CHR；16 位倒计时 IRQ；5B 三方波。
 */
final class Fme7 implements Cartridge {
    private final byte[] prg;
    private final byte[] chr;
    private final byte[] prgRam = new byte[0x2000];
    private final boolean chrRam;
    private final int[] chrBank = new int[8];
    private final int[] prgBank = new int[4];
    private int cmd;
    private int mirror;
    private int irqCount;
    private boolean irqCountOn;
    private boolean irqOn;
    private boolean irqLine;
    private int audioAddr;
    private final int[] ay = new int[16];
    private final int[] toneT = new int[3];
    private final boolean[] tone = {true, true, true};
    private int noiseT;
    private int noise = 1;
    private boolean noiseOut;
    private int envT;
    private int env;
    private int envDir = -1;

    Fme7(byte[] prg, byte[] chr, boolean chrRam) {
        this.prg = prg;
        this.chr = chr;
        this.chrRam = chrRam;
        int last = Math.max(0, prg.length / 0x2000 - 1);
        prgBank[1] = 0;
        prgBank[2] = Math.min(1, last);
        prgBank[3] = Math.min(2, last);
    }

    @Override
    public int cpuRead(int address) {
        address &= 0xFFFF;
        if (address >= 0x8000) {
            return prg[prgOffset(address)] & 0xFF;
        }
        if (address >= 0x6000) {
            if ((prgBank[0] & 0x40) != 0) {
                return prgRam[address - 0x6000] & 0xFF;
            }
            int banks = Math.max(1, prg.length / 0x2000);
            return prg[((prgBank[0] & 0x3F) % banks) * 0x2000 + (address - 0x6000)] & 0xFF;
        }
        return 0;
    }

    @Override
    public void cpuWrite(int address, int value) {
        address &= 0xFFFF;
        value &= 0xFF;
        if (address >= 0x6000 && address < 0x8000) {
            if ((prgBank[0] & 0x40) != 0) {
                prgRam[address - 0x6000] = (byte) value;
            }
            return;
        }
        switch (address & 0xE000) {
            case 0x8000 -> cmd = value & 0x0F;
            case 0xA000 -> writeCmd(value);
            case 0xC000 -> audioAddr = value & 0x0F;
            case 0xE000 -> ay[audioAddr] = value;
            default -> {
            }
        }
    }

    @Override
    public int ppuRead(int address) {
        int a = address & 0x1FFF;
        return chr[(chrBank[a >> 10] * 0x400 + (a & 0x3FF)) % chr.length] & 0xFF;
    }

    @Override
    public void ppuWrite(int address, int value) {
        if (chrRam) {
            int a = address & 0x1FFF;
            chr[(chrBank[a >> 10] * 0x400 + (a & 0x3FF)) % chr.length] = (byte) value;
        }
    }

    @Override
    public int nametableOffset(int address) {
        return Vrc24.mirrorNt(address, mirror);
    }

    @Override
    public void clockCpu() {
        if (irqCountOn) {
            irqCount = (irqCount - 1) & 0xFFFF;
            if (irqCount == 0xFFFF && irqOn) {
                irqLine = true;
            }
        }
        clockAy();
    }

    @Override
    public boolean irqAsserted() {
        return irqLine;
    }

    @Override
    public int expansionPulse() {
        int mix = ay[7];
        int sum = 0;
        for (int i = 0; i < 3; i++) {
            boolean t = (mix & (1 << i)) == 0;
            boolean n = (mix & (8 << i)) == 0;
            boolean on = (t && tone[i]) || (n && noiseOut) || (!t && !n);
            if (!on) {
                continue;
            }
            int v = ay[8 + i] & 0x1F;
            int level = (v & 0x10) != 0 ? env >> 2 : v & 0x0F;
            sum += level;
        }
        return sum;
    }

    @Override
    public void saveState(java.io.DataOutput out) throws java.io.IOException {
        out.writeInt(69);
        out.writeInt(prg.length);
        out.writeInt(chr.length);
        out.writeBoolean(chrRam);
        out.write(prgRam);
        if (chrRam) {
            out.write(chr);
        }
        out.writeInt(cmd);
        out.writeInt(mirror);
        out.writeInt(irqCount);
        out.writeBoolean(irqCountOn);
        out.writeBoolean(irqOn);
        out.writeBoolean(irqLine);
        for (int b : prgBank) {
            out.writeInt(b);
        }
        for (int b : chrBank) {
            out.writeInt(b);
        }
        out.writeInt(audioAddr);
        for (int r : ay) {
            out.writeInt(r);
        }
    }

    @Override
    public void loadState(java.io.DataInput in) throws java.io.IOException {
        if (in.readInt() != 69) {
            throw new java.io.IOException("存档不是 FME-7");
        }
        if (in.readInt() != prg.length || in.readInt() != chr.length || in.readBoolean() != chrRam) {
            throw new java.io.IOException("存档与当前盘不匹配");
        }
        in.readFully(prgRam);
        if (chrRam) {
            in.readFully(chr);
        }
        cmd = in.readInt();
        mirror = in.readInt();
        irqCount = in.readInt();
        irqCountOn = in.readBoolean();
        irqOn = in.readBoolean();
        irqLine = in.readBoolean();
        for (int i = 0; i < prgBank.length; i++) {
            prgBank[i] = in.readInt();
        }
        for (int i = 0; i < chrBank.length; i++) {
            chrBank[i] = in.readInt();
        }
        audioAddr = in.readInt();
        for (int i = 0; i < ay.length; i++) {
            ay[i] = in.readInt();
        }
    }

    private void writeCmd(int value) {
        if (cmd < 8) {
            chrBank[cmd] = value;
            return;
        }
        if (cmd < 12) {
            prgBank[cmd - 8] = value;
            return;
        }
        if (cmd == 12) {
            mirror = value & 3;
            return;
        }
        if (cmd == 13) {
            irqCountOn = (value & 1) != 0;
            irqOn = (value & 0x80) != 0;
            irqLine = false;
            return;
        }
        if (cmd == 14) {
            irqCount = (irqCount & 0xFF00) | value;
            return;
        }
        irqCount = (irqCount & 0xFF) | (value << 8);
    }

    private int prgOffset(int address) {
        int banks = Math.max(1, prg.length / 0x2000);
        int last = banks - 1;
        int slot;
        if (address >= 0xE000) {
            slot = last;
        } else if (address >= 0xC000) {
            slot = prgBank[3];
        } else if (address >= 0xA000) {
            slot = prgBank[2];
        } else {
            slot = prgBank[1];
        }
        return (slot % banks) * 0x2000 + (address & 0x1FFF);
    }

    private void clockAy() {
        for (int i = 0; i < 3; i++) {
            int p = ay[i * 2] | ((ay[i * 2 + 1] & 0x0F) << 8);
            if (p == 0) {
                p = 1;
            }
            if (--toneT[i] <= 0) {
                toneT[i] = p;
                tone[i] = !tone[i];
            }
        }
        int np = ay[6] & 0x1F;
        if (np == 0) {
            np = 1;
        }
        if (--noiseT <= 0) {
            noiseT = np;
            int bit = (noise ^ (noise >> 3)) & 1;
            noise = (noise >> 1) | (bit << 16);
            noiseOut = (noise & 1) != 0;
        }
        int ep = ay[11] | (ay[12] << 8);
        if (ep == 0) {
            ep = 1;
        }
        if (--envT <= 0) {
            envT = ep;
            env += envDir;
            if (env < 0 || env > 31) {
                int shape = ay[13] & 0x0F;
                if ((shape & 8) == 0) {
                    env = 0;
                    envDir = 0;
                } else if ((shape & 1) != 0) {
                    env = (shape & 2) != 0 ? 0 : 31;
                    envDir = 0;
                } else if ((shape & 2) != 0) {
                    envDir = -envDir;
                    env += envDir;
                } else {
                    env = envDir < 0 ? 31 : 0;
                }
            }
        }
    }
}
