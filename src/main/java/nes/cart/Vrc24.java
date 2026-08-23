package nes.cart;

/**
 * Mapper 21/23/25（VRC4）与 22（VRC2a）。地址线按位或兼容；22 的 CHR 右移 1。
 */
final class Vrc24 implements Cartridge {
    private final byte[] prg;
    private final byte[] chr;
    private final byte[] prgRam = new byte[0x2000];
    private final boolean chrRam;
    private final boolean hasIrq;
    private final boolean chrShift;
    private final int mapper;
    private final int[] chrBank = new int[8];
    private int prg0;
    private int prg1;
    private boolean prgSwap;
    private int mirror;
    private int irqLatch;
    private int irqCount;
    private int irqWait;
    private boolean irqOn;
    private boolean irqAfter;
    private boolean irqCycle;
    private boolean irqLine;

    Vrc24(byte[] prg, byte[] chr, boolean chrRam, int mapper, boolean hasIrq, boolean chrShift) {
        this.prg = prg;
        this.chr = chr;
        this.chrRam = chrRam;
        this.mapper = mapper;
        this.hasIrq = hasIrq;
        this.chrShift = chrShift;
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
        int r = decode(address);
        switch (address & 0xF000) {
            case 0x8000 -> prg0 = value;
            case 0x9000 -> {
                if (r == 0) {
                    mirror = value & 3;
                } else if ((r & 2) != 0) {
                    prgSwap = (value & 2) != 0;
                }
            }
            case 0xA000 -> prg1 = value;
            case 0xB000, 0xC000, 0xD000, 0xE000 -> {
                int idx = ((address - 0xB000) >> 12) * 2 + (r >> 1);
                if ((r & 1) != 0) {
                    chrBank[idx] = (chrBank[idx] & 0x0F) | ((value & 0x0F) << 4);
                } else {
                    chrBank[idx] = (chrBank[idx] & 0xF0) | (value & 0x0F);
                }
            }
            case 0xF000 -> {
                if (!hasIrq) {
                    return;
                }
                if (r == 0) {
                    irqLatch = (irqLatch & 0xF0) | (value & 0x0F);
                } else if (r == 1) {
                    irqLatch = (irqLatch & 0x0F) | ((value & 0x0F) << 4);
                } else if (r == 2) {
                    irqLine = false;
                    irqAfter = (value & 1) != 0;
                    irqOn = (value & 2) != 0;
                    irqCycle = (value & 4) != 0;
                    if (irqOn) {
                        irqCount = irqLatch;
                        irqWait = 341;
                    }
                } else {
                    irqLine = false;
                    irqOn = irqAfter;
                }
            }
            default -> {
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
        return mirrorNt(address, mirror);
    }

    @Override
    public void clockCpu() {
        if (!hasIrq || !irqOn) {
            return;
        }
        if (irqCycle) {
            clockIrq();
            return;
        }
        irqWait -= 3;
        if (irqWait <= 0) {
            irqWait += 341;
            clockIrq();
        }
    }

    @Override
    public boolean irqAsserted() {
        return irqLine;
    }

    @Override
    public void saveState(java.io.DataOutput out) throws java.io.IOException {
        out.writeInt(mapper);
        out.writeInt(prg.length);
        out.writeInt(chr.length);
        out.writeBoolean(chrRam);
        out.write(prgRam);
        if (chrRam) {
            out.write(chr);
        }
        out.writeInt(prg0);
        out.writeInt(prg1);
        out.writeBoolean(prgSwap);
        out.writeInt(mirror);
        for (int b : chrBank) {
            out.writeInt(b);
        }
        out.writeInt(irqLatch);
        out.writeInt(irqCount);
        out.writeInt(irqWait);
        out.writeBoolean(irqOn);
        out.writeBoolean(irqAfter);
        out.writeBoolean(irqCycle);
        out.writeBoolean(irqLine);
    }

    @Override
    public void loadState(java.io.DataInput in) throws java.io.IOException {
        if (in.readInt() != mapper) {
            throw new java.io.IOException("存档不是 VRC2/4");
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
        prgSwap = in.readBoolean();
        mirror = in.readInt();
        for (int i = 0; i < chrBank.length; i++) {
            chrBank[i] = in.readInt();
        }
        irqLatch = in.readInt();
        irqCount = in.readInt();
        irqWait = in.readInt();
        irqOn = in.readBoolean();
        irqAfter = in.readBoolean();
        irqCycle = in.readBoolean();
        irqLine = in.readBoolean();
    }

    private void clockIrq() {
        if (irqCount == 0xFF) {
            irqCount = irqLatch;
            irqLine = true;
        } else {
            irqCount++;
        }
    }

    private static int decode(int address) {
        int r = 0;
        if ((address & 0x01) != 0 || (address & 0x04) != 0 || (address & 0x40) != 0) {
            r |= 1;
        }
        if ((address & 0x02) != 0 || (address & 0x08) != 0 || (address & 0x80) != 0) {
            r |= 2;
        }
        return r;
    }

    private int prgOffset(int address) {
        int banks = Math.max(1, prg.length / 0x2000);
        int last = banks - 1;
        int second = Math.max(0, last - 1);
        int slot;
        if (address >= 0xE000) {
            slot = last;
        } else if (address >= 0xC000) {
            slot = prgSwap ? prg0 : second;
        } else if (address >= 0xA000) {
            slot = prg1;
        } else {
            slot = prgSwap ? second : prg0;
        }
        return (slot % banks) * 0x2000 + (address & 0x1FFF);
    }

    private int chrOffset(int address) {
        int a = address & 0x1FFF;
        int bank = chrBank[a >> 10];
        if (chrShift) {
            bank >>= 1;
        }
        return (bank * 0x400 + (a & 0x3FF)) % chr.length;
    }

    static int mirrorNt(int address, int mode) {
        int off = address & 0x0FFF;
        return switch (mode & 3) {
            case 0 -> off & 0x07FF;
            case 1 -> ((off & 0x800) >> 1) | (off & 0x3FF);
            case 2 -> off & 0x3FF;
            default -> 0x400 | (off & 0x3FF);
        };
    }
}
