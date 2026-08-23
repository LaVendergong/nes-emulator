package nes.cart;

/**
 * Mapper 85（VRC7）。3×8K PRG、8×1K CHR、VRC IRQ、YM2413 子集（6 路 + Konami 音色）。
 */
final class Vrc7 implements Cartridge {
    private final byte[] prg;
    private final byte[] chr;
    private final byte[] prgRam = new byte[0x2000];
    private final boolean chrRam;
    private final int[] chrBank = new int[8];
    private final int[] prgBank = new int[3];
    private final Ym2413 opll = new Ym2413(true);
    private int mirror;
    private int irqLatch;
    private int irqCount;
    private int irqWait;
    private boolean irqOn;
    private boolean irqAfter;
    private boolean irqCycle;
    private boolean irqLine;
    private int audioAddr;

    Vrc7(byte[] prg, byte[] chr, boolean chrRam) {
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
        if (address >= 0x6000 && address < 0x8000) {
            prgRam[address - 0x6000] = (byte) value;
            return;
        }
        int r = 0;
        if ((address & 0x10) != 0 || (address & 0x08) != 0) {
            r |= 1;
        }
        if ((address & 0x20) != 0 || (address & 0x04) != 0) {
            r |= 2;
        }
        switch (address & 0xF000) {
            case 0x8000 -> prgBank[r == 0 ? 0 : 1] = value;
            case 0x9000 -> {
                if (r == 0) {
                    prgBank[2] = value;
                } else if (r == 1) {
                    audioAddr = value;
                } else if (r == 3) {
                    opll.write(audioAddr, value);
                }
            }
            case 0xA000, 0xB000, 0xC000, 0xD000 -> {
                int base = ((address - 0xA000) >> 12) * 2;
                chrBank[base + (r == 0 ? 0 : 1)] = value;
            }
            case 0xE000 -> {
                if (r == 0) {
                    chrBank[6] = value;
                } else if (r == 1) {
                    chrBank[7] = value;
                } else if (r == 2) {
                    mirror = value & 3;
                }
            }
            case 0xF000 -> {
                if (r == 0) {
                    irqLatch = value;
                } else if (r == 1) {
                    irqLine = false;
                    irqAfter = (value & 1) != 0;
                    irqOn = (value & 2) != 0;
                    irqCycle = (value & 4) != 0;
                    if (irqOn) {
                        irqCount = irqLatch;
                        irqWait = 341;
                    }
                } else if (r == 2) {
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
        return Vrc24.mirrorNt(address, mirror);
    }

    @Override
    public void clockCpu() {
        opll.tick(1_789_773);
        if (!irqOn) {
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
    public int expansionPcm() {
        return opll.output();
    }

    @Override
    public void saveState(java.io.DataOutput out) throws java.io.IOException {
        out.writeInt(85);
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
        out.writeInt(irqLatch);
        out.writeInt(irqCount);
        out.writeInt(irqWait);
        out.writeBoolean(irqOn);
        out.writeBoolean(irqAfter);
        out.writeBoolean(irqCycle);
        out.writeBoolean(irqLine);
        out.writeInt(audioAddr);
        opll.save(out);
    }

    @Override
    public void loadState(java.io.DataInput in) throws java.io.IOException {
        if (in.readInt() != 85) {
            throw new java.io.IOException("存档不是 VRC7");
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
        irqLatch = in.readInt();
        irqCount = in.readInt();
        irqWait = in.readInt();
        irqOn = in.readBoolean();
        irqAfter = in.readBoolean();
        irqCycle = in.readBoolean();
        irqLine = in.readBoolean();
        audioAddr = in.readInt();
        opll.load(in);
    }

    private void clockIrq() {
        if (irqCount == 0xFF) {
            irqCount = irqLatch;
            irqLine = true;
        } else {
            irqCount++;
        }
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
