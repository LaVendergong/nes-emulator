package nes.cart;

/**
 * Mapper 19（Namco 163）。8K PRG、1K CHR、CIRAM/CHR nametable、15 位 IRQ、波形表。
 */
final class Namco163 implements Cartridge {
    private final byte[] prg;
    private final byte[] chr;
    private final byte[] prgRam = new byte[0x2000];
    private final byte[] iram = new byte[128];
    private final boolean chrRam;
    private final int[] chrBank = new int[8];
    private final int[] ntBank = new int[4];
    private final int[] prgBank = new int[3];
    private int iramAddr;
    private boolean iramInc = true;
    private int irqCount;
    private boolean irqOn;
    private boolean irqLine;
    private boolean soundOff;
    private int soundClock;
    private int mix;

    Namco163(byte[] prg, byte[] chr, boolean chrRam) {
        this.prg = prg;
        this.chr = chr;
        this.chrRam = chrRam;
        for (int i = 0; i < 4; i++) {
            ntBank[i] = 0x80 | (i & 1);
        }
    }

    @Override
    public int cpuRead(int address) {
        address &= 0xFFFF;
        if (address >= 0x8000) {
            return prg[prgOffset(address)] & 0xFF;
        }
        if (address >= 0x6000) {
            return prgRam[address - 0x6000] & 0xFF;
        }
        if ((address & 0xF800) == 0x4800) {
            int v = iram[iramAddr] & 0xFF;
            if (iramInc) {
                iramAddr = (iramAddr + 1) & 127;
            }
            return v;
        }
        if ((address & 0xF800) == 0x5000) {
            return irqCount & 0xFF;
        }
        if ((address & 0xF800) == 0x5800) {
            return ((irqCount >> 8) & 0x7F) | (irqOn ? 0x80 : 0);
        }
        return 0;
    }

    @Override
    public void cpuWrite(int address, int value) {
        address &= 0xFFFF;
        value &= 0xFF;
        if (address >= 0x6000 && address < 0x8000) {
            prgRam[address - 0x6000] = (byte) value;
            return;
        }
        if ((address & 0xF800) == 0x4800) {
            iram[iramAddr] = (byte) value;
            if (iramInc) {
                iramAddr = (iramAddr + 1) & 127;
            }
            return;
        }
        if ((address & 0xF800) == 0x5000) {
            irqCount = (irqCount & 0x7F00) | value;
            irqLine = false;
            return;
        }
        if ((address & 0xF800) == 0x5800) {
            irqCount = (irqCount & 0xFF) | ((value & 0x7F) << 8);
            irqOn = (value & 0x80) != 0;
            irqLine = false;
            return;
        }
        if (address < 0x8000) {
            return;
        }
        if (address < 0xC000) {
            chrBank[(address - 0x8000) >> 11] = value;
            return;
        }
        if (address < 0xE000) {
            ntBank[(address - 0xC000) >> 11] = value;
            return;
        }
        if (address < 0xE800) {
            prgBank[0] = value & 0x3F;
            soundOff = (value & 0x40) != 0;
            return;
        }
        if (address < 0xF000) {
            prgBank[1] = value & 0x3F;
            return;
        }
        if (address < 0xF800) {
            prgBank[2] = value & 0x3F;
            return;
        }
        iramAddr = value & 127;
        iramInc = (value & 0x80) != 0;
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
        int page = ntBank[(address >> 10) & 3];
        return ((page & 1) * 0x400) + (address & 0x3FF);
    }

    @Override
    public int nametableRead(int address) {
        int page = ntBank[(address >> 10) & 3];
        if ((page & 0x80) != 0) {
            return -1;
        }
        return chr[(page * 0x400 + (address & 0x3FF)) % chr.length] & 0xFF;
    }

    @Override
    public boolean nametableWrite(int address, int value) {
        int page = ntBank[(address >> 10) & 3];
        if ((page & 0x80) != 0) {
            return false;
        }
        if (chrRam) {
            chr[(page * 0x400 + (address & 0x3FF)) % chr.length] = (byte) value;
        }
        return true;
    }

    @Override
    public void clockCpu() {
        if (irqOn) {
            irqCount++;
            if (irqCount >= 0x8000) {
                irqCount = 0x8000;
                irqLine = true;
            }
        }
        if (soundOff) {
            mix = 0;
            return;
        }
        if (++soundClock < 15) {
            return;
        }
        soundClock = 0;
        int channels = ((iram[0x7F] >> 4) & 7) + 1;
        int sum = 0;
        for (int i = 8 - channels; i < 8; i++) {
            int base = 0x40 + i * 8;
            int freq = (iram[base] & 0xFF)
                    | ((iram[base + 2] & 0xFF) << 8)
                    | ((iram[base + 4] & 3) << 16);
            int phase = (iram[base + 1] & 0xFF)
                    | ((iram[base + 3] & 0xFF) << 8)
                    | ((iram[base + 5] & 0xFF) << 16);
            phase = (phase + freq) & 0xFFFFFF;
            iram[base + 1] = (byte) phase;
            iram[base + 3] = (byte) (phase >> 8);
            iram[base + 5] = (byte) (phase >> 16);
            int len = 256 - (iram[base + 4] & 0xFC);
            int addr = (iram[base + 6] & 0xFF) + ((phase >> 16) % Math.max(1, len));
            int sample = iram[(addr >> 1) & 127] & 0xFF;
            sample = (addr & 1) == 0 ? sample & 0x0F : sample >> 4;
            int vol = iram[base + 7] & 0x0F;
            sum += sample * vol;
        }
        mix = Math.min(127, sum >> 3);
    }

    @Override
    public boolean irqAsserted() {
        return irqLine;
    }

    @Override
    public int expansionPcm() {
        return mix;
    }

    @Override
    public void saveState(java.io.DataOutput out) throws java.io.IOException {
        out.writeInt(19);
        out.writeInt(prg.length);
        out.writeInt(chr.length);
        out.writeBoolean(chrRam);
        out.write(prgRam);
        out.write(iram);
        if (chrRam) {
            out.write(chr);
        }
        for (int b : prgBank) {
            out.writeInt(b);
        }
        for (int b : chrBank) {
            out.writeInt(b);
        }
        for (int b : ntBank) {
            out.writeInt(b);
        }
        out.writeInt(iramAddr);
        out.writeBoolean(iramInc);
        out.writeInt(irqCount);
        out.writeBoolean(irqOn);
        out.writeBoolean(irqLine);
        out.writeBoolean(soundOff);
    }

    @Override
    public void loadState(java.io.DataInput in) throws java.io.IOException {
        if (in.readInt() != 19) {
            throw new java.io.IOException("存档不是 Namco 163");
        }
        if (in.readInt() != prg.length || in.readInt() != chr.length || in.readBoolean() != chrRam) {
            throw new java.io.IOException("存档与当前盘不匹配");
        }
        in.readFully(prgRam);
        in.readFully(iram);
        if (chrRam) {
            in.readFully(chr);
        }
        for (int i = 0; i < prgBank.length; i++) {
            prgBank[i] = in.readInt();
        }
        for (int i = 0; i < chrBank.length; i++) {
            chrBank[i] = in.readInt();
        }
        for (int i = 0; i < ntBank.length; i++) {
            ntBank[i] = in.readInt();
        }
        iramAddr = in.readInt();
        iramInc = in.readBoolean();
        irqCount = in.readInt();
        irqOn = in.readBoolean();
        irqLine = in.readBoolean();
        soundOff = in.readBoolean();
    }

    private int prgOffset(int address) {
        int banks = Math.max(1, prg.length / 0x2000);
        int last = banks - 1;
        int slot;
        if (address >= 0xE000) {
            slot = last;
        } else if (address >= 0xC000) {
            slot = prgBank[2];
        } else if (address >= 0xA000) {
            slot = prgBank[1];
        } else {
            slot = prgBank[0];
        }
        return (slot % banks) * 0x2000 + (address & 0x1FFF);
    }
}
