package nes.cart;

/**
 * Mapper 87 / 140（Jaleco）。87 写 $6000 切 CHR（位对调）；140 写 $6000 切 32K PRG + 8K CHR。
 */
final class Jaleco implements Cartridge {
    private final byte[] prg;
    private final byte[] chr;
    private final byte[] prgRam = new byte[0x2000];
    private final boolean chrRam;
    private final boolean verticalMirroring;
    private final boolean mapper140;
    private int prgBank;
    private int chrBank;

    Jaleco(byte[] prg, byte[] chr, boolean chrRam, boolean verticalMirroring, boolean mapper140) {
        this.prg = prg;
        this.chr = chr;
        this.chrRam = chrRam;
        this.verticalMirroring = verticalMirroring;
        this.mapper140 = mapper140;
    }

    @Override
    public int cpuRead(int address) {
        address &= 0xFFFF;
        if (address >= 0x8000) {
            if (mapper140) {
                int banks = Math.max(1, prg.length / 0x8000);
                return prg[(prgBank % banks) * 0x8000 + (address & 0x7FFF)] & 0xFF;
            }
            return prg[(address - 0x8000) & (prg.length - 1)] & 0xFF;
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
        if (address >= 0x6000 && address < 0x8000) {
            if (mapper140) {
                int pb = Math.max(1, prg.length / 0x8000);
                int cb = Math.max(1, chr.length / 0x2000);
                prgBank = ((value >> 4) & 3) % pb;
                chrBank = (value & 0x0F) % cb;
            } else {
                int cb = Math.max(1, chr.length / 0x2000);
                chrBank = (((value & 2) >> 1) | ((value & 1) << 1)) % cb;
            }
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
    public void saveState(java.io.DataOutput out) throws java.io.IOException {
        out.writeInt(mapper140 ? 140 : 87);
        out.writeInt(prg.length);
        out.writeInt(chr.length);
        out.writeBoolean(chrRam);
        out.write(prgRam);
        if (chrRam) {
            out.write(chr);
        }
        out.writeInt(prgBank);
        out.writeInt(chrBank);
    }

    @Override
    public void loadState(java.io.DataInput in) throws java.io.IOException {
        if (in.readInt() != (mapper140 ? 140 : 87)) {
            throw new java.io.IOException("存档不是 Jaleco");
        }
        if (in.readInt() != prg.length || in.readInt() != chr.length || in.readBoolean() != chrRam) {
            throw new java.io.IOException("存档与当前盘不匹配");
        }
        in.readFully(prgRam);
        if (chrRam) {
            in.readFully(chr);
        }
        prgBank = in.readInt();
        chrBank = in.readInt();
    }
}
