package nes.cart;

/**
 * Mapper 6（FFE 16K PRG）/ 8（FFE 32K PRG）。写 $8000 同时切 PRG/CHR；$4501–$4503 CPU 周期 IRQ。
 */
final class Ffe implements Cartridge {
    private final byte[] prg;
    private final byte[] chr;
    private final byte[] prgRam = new byte[0x2000];
    private final boolean chrRam;
    private final boolean verticalMirroring;
    private final boolean prg32k;
    private int prgBank;
    private int chrBank;
    private int irqCount;
    private boolean irqOn;
    private boolean irqLine;

    Ffe(byte[] prg, byte[] chr, boolean chrRam, boolean verticalMirroring, boolean prg32k) {
        this.prg = prg;
        this.chr = chr;
        this.chrRam = chrRam;
        this.verticalMirroring = verticalMirroring;
        this.prg32k = prg32k;
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
        return 0;
    }

    @Override
    public void cpuWrite(int address, int value) {
        address &= 0xFFFF;
        value &= 0xFF;
        if (address == 0x4501) {
            irqOn = value != 0;
            if (!irqOn) {
                irqLine = false;
            }
            return;
        }
        if (address == 0x4502) {
            irqCount = (irqCount & 0xFF00) | value;
            return;
        }
        if (address == 0x4503) {
            irqCount = (irqCount & 0xFF) | (value << 8);
            irqOn = true;
            irqLine = false;
            return;
        }
        if (address >= 0x8000) {
            if (prg32k) {
                int pb = Math.max(1, prg.length / 0x8000);
                int cb = Math.max(1, chr.length / 0x2000);
                prgBank = ((value >> 3) & 7) % pb;
                chrBank = (value & 7) % cb;
            } else {
                int pb = Math.max(1, prg.length / 0x4000);
                int cb = Math.max(1, chr.length / 0x2000);
                prgBank = ((value >> 2) & 0x3F) % pb;
                chrBank = (value & 3) % cb;
            }
            return;
        }
        if (address >= 0x6000) {
            prgRam[address - 0x6000] = (byte) value;
        }
    }

    @Override
    public int ppuRead(int address) {
        return chr[(chrBank * 0x2000 + (address & 0x1FFF)) % chr.length] & 0xFF;
    }

    @Override
    public void ppuWrite(int address, int value) {
        if (chrRam) {
            chr[(chrBank * 0x2000 + (address & 0x1FFF)) % chr.length] = (byte) value;
        }
    }

    @Override
    public int nametableOffset(int address) {
        int off = address & 0x0FFF;
        if (verticalMirroring) {
            return off & 0x07FF;
        }
        return ((off & 0x800) >> 1) | (off & 0x3FF);
    }

    @Override
    public void clockCpu() {
        if (!irqOn) {
            return;
        }
        irqCount++;
        if (irqCount > 0xFFFF) {
            irqCount &= 0xFFFF;
            irqLine = true;
        }
    }

    @Override
    public boolean irqAsserted() {
        return irqLine;
    }

    @Override
    public void saveState(java.io.DataOutput out) throws java.io.IOException {
        out.writeInt(prg32k ? 8 : 6);
        out.writeInt(prg.length);
        out.writeInt(chr.length);
        out.writeBoolean(chrRam);
        out.write(prgRam);
        if (chrRam) {
            out.write(chr);
        }
        out.writeBoolean(verticalMirroring);
        out.writeInt(prgBank);
        out.writeInt(chrBank);
        out.writeInt(irqCount);
        out.writeBoolean(irqOn);
        out.writeBoolean(irqLine);
    }

    @Override
    public void loadState(java.io.DataInput in) throws java.io.IOException {
        if (in.readInt() != (prg32k ? 8 : 6)) {
            throw new java.io.IOException("存档不是 FFE");
        }
        if (in.readInt() != prg.length || in.readInt() != chr.length || in.readBoolean() != chrRam) {
            throw new java.io.IOException("存档与当前盘不匹配");
        }
        in.readFully(prgRam);
        if (chrRam) {
            in.readFully(chr);
        }
        if (in.readBoolean() != verticalMirroring) {
            throw new java.io.IOException("存档镜像不匹配");
        }
        prgBank = in.readInt();
        chrBank = in.readInt();
        irqCount = in.readInt();
        irqOn = in.readBoolean();
        irqLine = in.readBoolean();
    }

    private int prgOffset(int address) {
        if (prg32k) {
            int banks = Math.max(1, prg.length / 0x8000);
            return (prgBank % banks) * 0x8000 + (address & 0x7FFF);
        }
        int banks = Math.max(1, prg.length / 0x4000);
        int last = banks - 1;
        int slot = address >= 0xC000 ? last : prgBank % banks;
        return slot * 0x4000 + (address & 0x3FFF);
    }
}
