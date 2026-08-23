package nes.cart;

/**
 * Mapper 73（VRC3）。16K PRG，$C000 固定；16 位 CPU 周期 IRQ。
 */
final class Vrc3 implements Cartridge {
    private final byte[] prg;
    private final byte[] chr;
    private final byte[] prgRam = new byte[0x2000];
    private final boolean chrRam;
    private final boolean verticalMirroring;
    private int bank;
    private int irqLatch;
    private int irqCount;
    private boolean irqOn;
    private boolean irq8;
    private boolean irqAfter;
    private boolean irqLine;

    Vrc3(byte[] prg, byte[] chr, boolean chrRam, boolean verticalMirroring) {
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
            int last = banks - 1;
            int slot = address >= 0xC000 ? last : bank % banks;
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
        value &= 0xFF;
        switch (address & 0xF000) {
            case 0x8000 -> irqLatch = (irqLatch & 0xFFF0) | (value & 0x0F);
            case 0x9000 -> irqLatch = (irqLatch & 0xFF0F) | ((value & 0x0F) << 4);
            case 0xA000 -> irqLatch = (irqLatch & 0xF0FF) | ((value & 0x0F) << 8);
            case 0xB000 -> irqLatch = (irqLatch & 0x0FFF) | ((value & 0x0F) << 12);
            case 0xC000 -> {
                irqLine = false;
                irqAfter = (value & 1) != 0;
                irqOn = (value & 2) != 0;
                irq8 = (value & 4) != 0;
                if (irqOn) {
                    irqCount = irqLatch;
                }
            }
            case 0xD000 -> {
                irqLine = false;
                irqOn = irqAfter;
            }
            case 0xF000 -> {
                int banks = Math.max(1, prg.length / 0x4000);
                bank = value % banks;
            }
            default -> {
                if (address >= 0x6000 && address < 0x8000) {
                    prgRam[address - 0x6000] = (byte) value;
                }
            }
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
    public void clockCpu() {
        if (!irqOn) {
            return;
        }
        if (irq8) {
            int n = (irqCount + 1) & 0xFF;
            irqCount = (irqCount & 0xFF00) | n;
            if (n == 0) {
                irqCount = (irqCount & 0xFF00) | (irqLatch & 0xFF);
                irqLine = true;
            }
            return;
        }
        irqCount = (irqCount + 1) & 0xFFFF;
        if (irqCount == 0) {
            irqCount = irqLatch;
            irqLine = true;
        }
    }

    @Override
    public boolean irqAsserted() {
        return irqLine;
    }

    @Override
    public void saveState(java.io.DataOutput out) throws java.io.IOException {
        out.writeInt(73);
        out.writeInt(prg.length);
        out.writeInt(chr.length);
        out.writeBoolean(chrRam);
        out.write(prgRam);
        if (chrRam) {
            out.write(chr);
        }
        out.writeInt(bank);
        out.writeInt(irqLatch);
        out.writeInt(irqCount);
        out.writeBoolean(irqOn);
        out.writeBoolean(irq8);
        out.writeBoolean(irqAfter);
        out.writeBoolean(irqLine);
    }

    @Override
    public void loadState(java.io.DataInput in) throws java.io.IOException {
        if (in.readInt() != 73) {
            throw new java.io.IOException("存档不是 VRC3");
        }
        if (in.readInt() != prg.length || in.readInt() != chr.length || in.readBoolean() != chrRam) {
            throw new java.io.IOException("存档与当前盘不匹配");
        }
        in.readFully(prgRam);
        if (chrRam) {
            in.readFully(chr);
        }
        bank = in.readInt();
        irqLatch = in.readInt();
        irqCount = in.readInt();
        irqOn = in.readBoolean();
        irq8 = in.readBoolean();
        irqAfter = in.readBoolean();
        irqLine = in.readBoolean();
    }
}
