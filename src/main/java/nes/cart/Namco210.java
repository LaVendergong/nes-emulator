package nes.cart;

/**
 * Mapper 210：Namco 175（焊死镜像）/ 340（$E000 bit6–7 镜像）。无 IRQ、无波形表。
 */
final class Namco210 implements Cartridge {
    private final byte[] prg;
    private final byte[] chr;
    private final byte[] prgRam = new byte[0x2000];
    private final boolean chrRam;
    private final boolean namco175;
    private final boolean verticalMirroring;
    private final int[] chrBank = new int[8];
    private final int[] prgBank = new int[3];
    private int mirror;

    Namco210(byte[] prg, byte[] chr, boolean chrRam, boolean verticalMirroring, boolean namco175) {
        this.prg = prg;
        this.chr = chr;
        this.chrRam = chrRam;
        this.verticalMirroring = verticalMirroring;
        this.namco175 = namco175;
        this.mirror = verticalMirroring ? 1 : 3;
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
        if (address >= 0x6000 && address < 0x8000) {
            prgRam[address - 0x6000] = (byte) value;
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
            return;
        }
        if (address < 0xE800) {
            prgBank[0] = value & 0x3F;
            if (!namco175) {
                mirror = (value >> 6) & 3;
            }
            return;
        }
        if (address < 0xF000) {
            prgBank[1] = value & 0x3F;
            return;
        }
        if (address < 0xF800) {
            prgBank[2] = value & 0x3F;
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
        if (namco175) {
            int off = address & 0x0FFF;
            if (verticalMirroring) {
                return off & 0x07FF;
            }
            return ((off & 0x800) >> 1) | (off & 0x3FF);
        }
        return switch (mirror) {
            case 0 -> address & 0x3FF;
            case 1 -> address & 0x7FF;
            case 2 -> 0x400 | (address & 0x3FF);
            default -> ((address & 0x800) >> 1) | (address & 0x3FF);
        };
    }

    @Override
    public void saveState(java.io.DataOutput out) throws java.io.IOException {
        out.writeInt(namco175 ? 175 : 340);
        out.writeInt(prg.length);
        out.writeInt(chr.length);
        out.writeBoolean(chrRam);
        out.write(prgRam);
        if (chrRam) {
            out.write(chr);
        }
        for (int b : prgBank) {
            out.writeInt(b);
        }
        for (int b : chrBank) {
            out.writeInt(b);
        }
        out.writeInt(mirror);
    }

    @Override
    public void loadState(java.io.DataInput in) throws java.io.IOException {
        if (in.readInt() != (namco175 ? 175 : 340)) {
            throw new java.io.IOException("存档不是 Namco 210");
        }
        if (in.readInt() != prg.length || in.readInt() != chr.length || in.readBoolean() != chrRam) {
            throw new java.io.IOException("存档与当前盘不匹配");
        }
        in.readFully(prgRam);
        if (chrRam) {
            in.readFully(chr);
        }
        for (int i = 0; i < prgBank.length; i++) {
            prgBank[i] = in.readInt();
        }
        for (int i = 0; i < chrBank.length; i++) {
            chrBank[i] = in.readInt();
        }
        mirror = in.readInt();
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
