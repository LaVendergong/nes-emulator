package nes.cart;

/**
 * Mapper 180（UNROM 反相）。$8000 固定第 0 页，$C000 可切。
 */
final class Unrom180 implements Cartridge {
    private final byte[] prg;
    private final byte[] chr;
    private final byte[] prgRam = new byte[0x2000];
    private final boolean chrRam;
    private final boolean verticalMirroring;
    private int bank;

    Unrom180(byte[] prg, byte[] chr, boolean chrRam, boolean verticalMirroring) {
        this.prg = prg;
        this.chr = chr;
        this.chrRam = chrRam;
        this.verticalMirroring = verticalMirroring;
    }

    @Override
    public int cpuRead(int address) {
        address &= 0xFFFF;
        if (address >= 0x8000) {
            int banks = Math.max(1, prg.length / 0x4000);
            int slot = address < 0xC000 ? 0 : bank % banks;
            return prg[slot * 0x4000 + (address & 0x3FFF)] & 0xFF;
        }
        if (address >= 0x6000) {
            return prgRam[address - 0x6000] & 0xFF;
        }
        return 0;
    }

    @Override
    public void cpuWrite(int address, int value) {
        address &= 0xFFFF;
        if (address >= 0x8000) {
            int banks = Math.max(1, prg.length / 0x4000);
            bank = value % banks;
            return;
        }
        if (address >= 0x6000) {
            prgRam[address - 0x6000] = (byte) value;
        }
    }

    @Override
    public int ppuRead(int address) {
        return chr[address & (chr.length - 1)] & 0xFF;
    }

    @Override
    public void ppuWrite(int address, int value) {
        if (chrRam) {
            chr[address & (chr.length - 1)] = (byte) value;
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
        out.writeInt(180);
        out.writeInt(prg.length);
        out.writeInt(chr.length);
        out.writeBoolean(chrRam);
        out.write(prgRam);
        if (chrRam) {
            out.write(chr);
        }
        out.writeInt(bank);
    }

    @Override
    public void loadState(java.io.DataInput in) throws java.io.IOException {
        if (in.readInt() != 180) {
            throw new java.io.IOException("存档不是 UNROM-180");
        }
        if (in.readInt() != prg.length || in.readInt() != chr.length || in.readBoolean() != chrRam) {
            throw new java.io.IOException("存档与当前盘不匹配");
        }
        in.readFully(prgRam);
        if (chrRam) {
            in.readFully(chr);
        }
        bank = in.readInt();
    }
}
