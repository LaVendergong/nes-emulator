package nes.cart;

/**
 * Mapper 4（MMC3）。8K/1K/2K 银行；IRQ 靠 PPU A12 上升沿。
 * ponytail: A12 须先连续低 8 个 PPU dot 才认上升。天花板：电气滤波按芯片。升级：按实测脉宽。
 */
final class Mmc3 implements Cartridge {
    private final byte[] prg;
    private final byte[] chr;
    private final byte[] prgRam = new byte[0x2000];
    private final boolean chrRam;
    private boolean verticalMirroring;
    private final int[] bank = new int[8];
    private int bankSelect;
    private int irqLatch;
    private int irqCounter;
    private boolean irqReload;
    private boolean irqEnabled;
    private boolean irqLine;
    private int a12Low;

    Mmc3(byte[] prg, byte[] chr, boolean chrRam, boolean verticalMirroring) {
        this.prg = prg;
        this.chr = chr;
        this.chrRam = chrRam;
        this.verticalMirroring = verticalMirroring;
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
        if (address >= 0x8000) {
            boolean odd = (address & 1) != 0;
            int pair = address & 0xE000;
            if (pair == 0x8000) {
                if (!odd) {
                    bankSelect = value;
                } else {
                    bank[bankSelect & 7] = value;
                }
            } else if (pair == 0xA000) {
                if (!odd) {
                    verticalMirroring = (value & 1) == 0;
                }
            } else if (pair == 0xC000) {
                if (!odd) {
                    irqLatch = value;
                } else {
                    irqReload = true;
                }
            } else if (pair == 0xE000) {
                if (!odd) {
                    irqEnabled = false;
                    irqLine = false;
                } else {
                    irqEnabled = true;
                }
            }
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
    public void onPpuA12(boolean high) {
        if (high) {
            if (a12Low >= 8) {
                onPpuA12Rise();
            }
            a12Low = 0;
        } else if (a12Low < 8) {
            a12Low++;
        }
    }

    @Override
    public void onPpuA12Rise() {
        if (irqReload || irqCounter == 0) {
            irqCounter = irqLatch;
        } else {
            irqCounter--;
        }
        irqReload = false;
        if (irqCounter == 0 && irqEnabled) {
            irqLine = true;
        }
    }

    @Override
    public boolean irqAsserted() {
        return irqLine;
    }

    @Override
    public void saveState(java.io.DataOutput out) throws java.io.IOException {
        out.writeInt(4);
        out.writeInt(prg.length);
        out.writeInt(chr.length);
        out.writeBoolean(chrRam);
        out.write(prgRam);
        if (chrRam) {
            out.write(chr);
        }
        out.writeBoolean(verticalMirroring);
        out.writeInt(bankSelect);
        for (int b : bank) {
            out.writeInt(b);
        }
        out.writeInt(irqLatch);
        out.writeInt(irqCounter);
        out.writeBoolean(irqReload);
        out.writeBoolean(irqEnabled);
        out.writeBoolean(irqLine);
        out.writeInt(a12Low);
    }

    @Override
    public void loadState(java.io.DataInput in) throws java.io.IOException {
        if (in.readInt() != 4) {
            throw new java.io.IOException("存档不是 MMC3");
        }
        if (in.readInt() != prg.length || in.readInt() != chr.length || in.readBoolean() != chrRam) {
            throw new java.io.IOException("存档与当前盘不匹配");
        }
        in.readFully(prgRam);
        if (chrRam) {
            in.readFully(chr);
        }
        verticalMirroring = in.readBoolean();
        bankSelect = in.readInt();
        for (int i = 0; i < bank.length; i++) {
            bank[i] = in.readInt();
        }
        irqLatch = in.readInt();
        irqCounter = in.readInt();
        irqReload = in.readBoolean();
        irqEnabled = in.readBoolean();
        irqLine = in.readBoolean();
        a12Low = in.readInt();
    }

    private int prgOffset(int address) {
        int banks = Math.max(1, prg.length / 0x2000);
        int last = banks - 1;
        int second = Math.max(0, last - 1);
        int swap = bank[6] % banks;
        int mid = bank[7] % banks;
        int slot;
        if (address >= 0xE000) {
            slot = last;
        } else if (address >= 0xC000) {
            slot = (bankSelect & 0x40) != 0 ? swap : second;
        } else if (address >= 0xA000) {
            slot = mid;
        } else {
            slot = (bankSelect & 0x40) != 0 ? second : swap;
        }
        return slot * 0x2000 + (address & 0x1FFF);
    }

    private int chrOffset(int address) {
        int a = address & 0x1FFF;
        boolean invert = (bankSelect & 0x80) != 0;
        int r;
        int mask;
        if (!invert) {
            if (a < 0x0800) {
                r = bank[0] & ~1;
                mask = 0x7FF;
            } else if (a < 0x1000) {
                r = bank[1] & ~1;
                mask = 0x7FF;
            } else if (a < 0x1400) {
                r = bank[2];
                mask = 0x3FF;
            } else if (a < 0x1800) {
                r = bank[3];
                mask = 0x3FF;
            } else if (a < 0x1C00) {
                r = bank[4];
                mask = 0x3FF;
            } else {
                r = bank[5];
                mask = 0x3FF;
            }
        } else if (a < 0x0400) {
            r = bank[2];
            mask = 0x3FF;
        } else if (a < 0x0800) {
            r = bank[3];
            mask = 0x3FF;
        } else if (a < 0x0C00) {
            r = bank[4];
            mask = 0x3FF;
        } else if (a < 0x1000) {
            r = bank[5];
            mask = 0x3FF;
        } else if (a < 0x1800) {
            r = bank[0] & ~1;
            mask = 0x7FF;
        } else {
            r = bank[1] & ~1;
            mask = 0x7FF;
        }
        return (r * 0x400 + (a & mask)) % chr.length;
    }
}
