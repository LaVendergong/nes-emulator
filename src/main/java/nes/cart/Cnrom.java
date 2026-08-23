package nes.cart;

/**
 * Mapper 3（CNROM）。PRG 固定；写 $8000–$FFFF 切 8K CHR。
 * ponytail: 直接用写入值选银行，不模拟总线冲突。天花板：极少数要 ROM&data。升级：按子 mapper 做冲突。
 */
final class Cnrom implements Cartridge {
    private final byte[] prg;
    private final byte[] chr;
    private final byte[] prgRam = new byte[0x2000];
    private final boolean chrRam;
    private final boolean verticalMirroring;
    private int bank;

    Cnrom(byte[] prg, byte[] chr, boolean chrRam, boolean verticalMirroring) {
        this.prg = prg;
        this.chr = chr;
        this.chrRam = chrRam;
        this.verticalMirroring = verticalMirroring;
    }

    @Override
    public int cpuRead(int address) {
        address &= 0xFFFF;
        if (address >= 0x8000) {
            int off = address - 0x8000;
            if (prg.length == 0x4000) {
                off &= 0x3FFF;
            } else {
                off &= prg.length - 1;
            }
            return prg[off] & 0xFF;
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
            int banks = Math.max(1, chr.length / 0x2000);
            bank = (value & 0xFF) % banks;
            return;
        }
        if (address >= 0x6000) {
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
        out.writeInt(3);
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
        if (in.readInt() != 3) {
            throw new java.io.IOException("存档不是 CNROM");
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

    private int chrOffset(int address) {
        return (bank * 0x2000 + (address & 0x1FFF)) % chr.length;
    }
}
