package nes.cart;

/**
 * Mapper 13（CPROM）。32K PRG；$0000 固定 4K CHR RAM，$1000 可切 4K。
 */
final class Cprom implements Cartridge {
    private final byte[] prg;
    private final byte[] chr;
    private final byte[] prgRam = new byte[0x2000];
    private final boolean verticalMirroring;
    private int bank;

    Cprom(byte[] prg, byte[] chr, boolean verticalMirroring) {
        this.prg = prg;
        this.chr = chr;
        this.verticalMirroring = verticalMirroring;
    }

    @Override
    public int cpuRead(int address) {
        address &= 0xFFFF;
        if (address >= 0x8000) {
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
        if (address >= 0x8000) {
            int banks = Math.max(1, chr.length / 0x1000);
            bank = (value & 3) % banks;
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
        chr[chrOffset(address)] = (byte) value;
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
        out.writeInt(13);
        out.write(prgRam);
        out.write(chr);
        out.writeInt(bank);
    }

    @Override
    public void loadState(java.io.DataInput in) throws java.io.IOException {
        if (in.readInt() != 13) {
            throw new java.io.IOException("存档不是 CPROM");
        }
        in.readFully(prgRam);
        in.readFully(chr);
        bank = in.readInt();
    }

    private int chrOffset(int address) {
        int a = address & 0x1FFF;
        int slot = a < 0x1000 ? 0 : bank;
        return (slot * 0x1000 + (a & 0x0FFF)) % chr.length;
    }
}
