package nes.cart;

/**
 * Mapper 71（Camerica）。$C000 切 16K PRG；$8000–$9FFF bit4 单屏（Fire Hawk）。
 */
final class Camerica implements Cartridge {
    private final byte[] prg;
    private final byte[] chr;
    private final byte[] prgRam = new byte[0x2000];
    private final boolean chrRam;
    private boolean page1;
    private boolean single;
    private int bank;

    Camerica(byte[] prg, byte[] chr, boolean chrRam) {
        this.prg = prg;
        this.chr = chr;
        this.chrRam = chrRam;
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
        if (address >= 0xC000) {
            int banks = Math.max(1, prg.length / 0x4000);
            bank = value % banks;
            return;
        }
        if (address >= 0x8000 && address <= 0x9FFF) {
            single = true;
            page1 = (value & 0x10) != 0;
            return;
        }
        if (address >= 0x6000 && address < 0x8000) {
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
        if (single) {
            return (page1 ? 0x400 : 0) | (address & 0x3FF);
        }
        return ((address & 0x800) >> 1) | (address & 0x3FF);
    }

    @Override
    public void saveState(java.io.DataOutput out) throws java.io.IOException {
        out.writeInt(71);
        out.writeInt(prg.length);
        out.writeInt(chr.length);
        out.writeBoolean(chrRam);
        out.write(prgRam);
        if (chrRam) {
            out.write(chr);
        }
        out.writeInt(bank);
        out.writeBoolean(single);
        out.writeBoolean(page1);
    }

    @Override
    public void loadState(java.io.DataInput in) throws java.io.IOException {
        if (in.readInt() != 71) {
            throw new java.io.IOException("存档不是 Camerica");
        }
        if (in.readInt() != prg.length || in.readInt() != chr.length || in.readBoolean() != chrRam) {
            throw new java.io.IOException("存档与当前盘不匹配");
        }
        in.readFully(prgRam);
        if (chrRam) {
            in.readFully(chr);
        }
        bank = in.readInt();
        single = in.readBoolean();
        page1 = in.readBoolean();
    }

    private int prgOffset(int address) {
        int banks = Math.max(1, prg.length / 0x4000);
        int last = banks - 1;
        int slot = address >= 0xC000 ? last : bank % banks;
        return slot * 0x4000 + (address & 0x3FFF);
    }
}
