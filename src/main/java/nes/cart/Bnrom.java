package nes.cart;

/**
 * Mapper 34：CHR RAM 为 BNROM（写 $8000 切 32K PRG）；否则 NINA-001（$7FFD–$7FFF）。
 */
final class Bnrom implements Cartridge {
    private final byte[] prg;
    private final byte[] chr;
    private final byte[] prgRam = new byte[0x2000];
    private final boolean chrRam;
    private final boolean verticalMirroring;
    private int prgBank;
    private int chr0;
    private int chr1;

    Bnrom(byte[] prg, byte[] chr, boolean chrRam, boolean verticalMirroring) {
        this.prg = prg;
        this.chr = chr;
        this.chrRam = chrRam;
        this.verticalMirroring = verticalMirroring;
    }

    @Override
    public int cpuRead(int address) {
        address &= 0xFFFF;
        if (address >= 0x8000) {
            int banks = Math.max(1, prg.length / 0x8000);
            return prg[(prgBank % banks) * 0x8000 + (address & 0x7FFF)] & 0xFF;
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
        if (chrRam && address >= 0x8000) {
            int banks = Math.max(1, prg.length / 0x8000);
            prgBank = value % banks;
            return;
        }
        if (!chrRam && address == 0x7FFD) {
            int banks = Math.max(1, prg.length / 0x8000);
            prgBank = value % banks;
            return;
        }
        if (!chrRam && address == 0x7FFE) {
            chr0 = value;
            return;
        }
        if (!chrRam && address == 0x7FFF) {
            chr1 = value;
            return;
        }
        if (address >= 0x6000 && address < 0x8000) {
            prgRam[address - 0x6000] = (byte) value;
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
        int off = address & 0x0FFF;
        if (verticalMirroring) {
            return off & 0x07FF;
        }
        return ((off & 0x800) >> 1) | (off & 0x3FF);
    }

    @Override
    public void saveState(java.io.DataOutput out) throws java.io.IOException {
        out.writeInt(34);
        out.writeInt(prg.length);
        out.writeInt(chr.length);
        out.writeBoolean(chrRam);
        out.write(prgRam);
        if (chrRam) {
            out.write(chr);
        }
        out.writeInt(prgBank);
        out.writeInt(chr0);
        out.writeInt(chr1);
    }

    @Override
    public void loadState(java.io.DataInput in) throws java.io.IOException {
        if (in.readInt() != 34) {
            throw new java.io.IOException("存档不是 mapper 34");
        }
        if (in.readInt() != prg.length || in.readInt() != chr.length || in.readBoolean() != chrRam) {
            throw new java.io.IOException("存档与当前盘不匹配");
        }
        in.readFully(prgRam);
        if (chrRam) {
            in.readFully(chr);
        }
        prgBank = in.readInt();
        chr0 = in.readInt();
        chr1 = in.readInt();
    }

    private int chrOffset(int address) {
        if (chrRam) {
            return address & (chr.length - 1);
        }
        int banks = Math.max(1, chr.length / 0x1000);
        int slot = (address & 0x1000) == 0 ? chr0 : chr1;
        return (slot % banks) * 0x1000 + (address & 0x0FFF);
    }
}
