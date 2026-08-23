package nes.cart;

/**
 * Mapper 75（VRC1）。3×8K PRG、2×4K CHR，写 $9000 带 CHR 高位与镜像。
 */
final class Vrc1 implements Cartridge {
    private final byte[] prg;
    private final byte[] chr;
    private final byte[] prgRam = new byte[0x2000];
    private final boolean chrRam;
    private int prg0;
    private int prg1;
    private int prg2;
    private int chr0;
    private int chr1;
    private boolean horizontal;

    Vrc1(byte[] prg, byte[] chr, boolean chrRam) {
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
        int banks = Math.max(1, prg.length / 0x2000);
        switch (address & 0xF000) {
            case 0x8000 -> prg0 = value % banks;
            case 0x9000 -> {
                horizontal = (value & 1) != 0;
                chr0 = (chr0 & 0x0F) | ((value & 2) << 3);
                chr1 = (chr1 & 0x0F) | ((value & 4) << 2);
            }
            case 0xA000 -> prg1 = value % banks;
            case 0xC000 -> prg2 = value % banks;
            case 0xE000 -> chr0 = (chr0 & 0x10) | (value & 0x0F);
            case 0xF000 -> chr1 = (chr1 & 0x10) | (value & 0x0F);
            default -> {
                if (address >= 0x6000 && address < 0x8000) {
                    prgRam[address - 0x6000] = (byte) value;
                }
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
        int off = address & 0x0FFF;
        if (horizontal) {
            return ((off & 0x800) >> 1) | (off & 0x3FF);
        }
        return off & 0x07FF;
    }

    @Override
    public void saveState(java.io.DataOutput out) throws java.io.IOException {
        out.writeInt(75);
        out.writeInt(prg.length);
        out.writeInt(chr.length);
        out.writeBoolean(chrRam);
        out.write(prgRam);
        if (chrRam) {
            out.write(chr);
        }
        out.writeInt(prg0);
        out.writeInt(prg1);
        out.writeInt(prg2);
        out.writeInt(chr0);
        out.writeInt(chr1);
        out.writeBoolean(horizontal);
    }

    @Override
    public void loadState(java.io.DataInput in) throws java.io.IOException {
        if (in.readInt() != 75) {
            throw new java.io.IOException("存档不是 VRC1");
        }
        if (in.readInt() != prg.length || in.readInt() != chr.length || in.readBoolean() != chrRam) {
            throw new java.io.IOException("存档与当前盘不匹配");
        }
        in.readFully(prgRam);
        if (chrRam) {
            in.readFully(chr);
        }
        prg0 = in.readInt();
        prg1 = in.readInt();
        prg2 = in.readInt();
        chr0 = in.readInt();
        chr1 = in.readInt();
        horizontal = in.readBoolean();
    }

    private int prgOffset(int address) {
        int banks = Math.max(1, prg.length / 0x2000);
        int last = banks - 1;
        int slot;
        if (address >= 0xE000) {
            slot = last;
        } else if (address >= 0xC000) {
            slot = prg2;
        } else if (address >= 0xA000) {
            slot = prg1;
        } else {
            slot = prg0;
        }
        return (slot % banks) * 0x2000 + (address & 0x1FFF);
    }

    private int chrOffset(int address) {
        int a = address & 0x1FFF;
        int slot = a < 0x1000 ? chr0 : chr1;
        return (slot * 0x1000 + (a & 0x0FFF)) % chr.length;
    }
}
