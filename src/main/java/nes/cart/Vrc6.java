package nes.cart;

/**
 * Mapper 24/26（VRC6）。16K+8K PRG、8×1K CHR、VRC IRQ、两方波+锯齿。
 */
final class Vrc6 implements Cartridge {
    private final byte[] prg;
    private final byte[] chr;
    private final byte[] prgRam = new byte[0x2000];
    private final boolean chrRam;
    private final boolean swap;
    private final int[] chrBank = new int[8];
    private int prg16;
    private int prg8;
    private int mirror;
    private int irqLatch;
    private int irqCount;
    private int irqWait;
    private boolean irqOn;
    private boolean irqAfter;
    private boolean irqCycle;
    private boolean irqLine;
    private final Pulse pulse1 = new Pulse();
    private final Pulse pulse2 = new Pulse();
    private final Saw saw = new Saw();
    private boolean halt;
    private boolean freq16;

    Vrc6(byte[] prg, byte[] chr, boolean chrRam, boolean swap) {
        this.prg = prg;
        this.chr = chr;
        this.chrRam = chrRam;
        this.swap = swap;
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
        int a = remap(address);
        switch (a & 0xF003) {
            case 0x8000 -> prg16 = value;
            case 0x9000 -> pulse1.ctrl = value;
            case 0x9001 -> pulse1.period = (pulse1.period & 0xF00) | value;
            case 0x9002 -> {
                pulse1.period = (pulse1.period & 0xFF) | ((value & 0x0F) << 8);
                pulse1.on = (value & 0x80) != 0;
            }
            case 0x9003 -> {
                halt = (value & 1) != 0;
                freq16 = (value & 2) != 0;
            }
            case 0xA000 -> pulse2.ctrl = value;
            case 0xA001 -> pulse2.period = (pulse2.period & 0xF00) | value;
            case 0xA002 -> {
                pulse2.period = (pulse2.period & 0xFF) | ((value & 0x0F) << 8);
                pulse2.on = (value & 0x80) != 0;
            }
            case 0xB000 -> saw.rate = value & 0x3F;
            case 0xB001 -> saw.period = (saw.period & 0xF00) | value;
            case 0xB002 -> {
                saw.period = (saw.period & 0xFF) | ((value & 0x0F) << 8);
                saw.on = (value & 0x80) != 0;
            }
            case 0xB003 -> mirror = (value >> 2) & 3;
            case 0xC000 -> prg8 = value;
            case 0xD000, 0xD001, 0xD002, 0xD003 -> chrBank[a & 3] = value;
            case 0xE000, 0xE001, 0xE002, 0xE003 -> chrBank[4 + (a & 3)] = value;
            case 0xF000 -> irqLatch = value;
            case 0xF001 -> {
                irqLine = false;
                irqAfter = (value & 1) != 0;
                irqOn = (value & 2) != 0;
                irqCycle = (value & 4) != 0;
                if (irqOn) {
                    irqCount = irqLatch;
                    irqWait = 341;
                }
            }
            case 0xF002 -> {
                irqLine = false;
                irqOn = irqAfter;
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
        if (!halt) {
            int steps = freq16 ? 16 : 1;
            for (int i = 0; i < steps; i++) {
                pulse1.clock();
                pulse2.clock();
                saw.clock();
            }
        }
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
    public int expansionPulse() {
        return pulse1.output() + pulse2.output();
    }

    @Override
    public int expansionPcm() {
        return saw.output();
    }

    @Override
    public void saveState(java.io.DataOutput out) throws java.io.IOException {
        out.writeInt(swap ? 26 : 24);
        out.writeInt(prg.length);
        out.writeInt(chr.length);
        out.writeBoolean(chrRam);
        out.write(prgRam);
        if (chrRam) {
            out.write(chr);
        }
        out.writeInt(prg16);
        out.writeInt(prg8);
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
        out.writeBoolean(halt);
        out.writeBoolean(freq16);
        pulse1.save(out);
        pulse2.save(out);
        saw.save(out);
    }

    @Override
    public void loadState(java.io.DataInput in) throws java.io.IOException {
        if (in.readInt() != (swap ? 26 : 24)) {
            throw new java.io.IOException("存档不是 VRC6");
        }
        if (in.readInt() != prg.length || in.readInt() != chr.length || in.readBoolean() != chrRam) {
            throw new java.io.IOException("存档与当前盘不匹配");
        }
        in.readFully(prgRam);
        if (chrRam) {
            in.readFully(chr);
        }
        prg16 = in.readInt();
        prg8 = in.readInt();
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
        halt = in.readBoolean();
        freq16 = in.readBoolean();
        pulse1.load(in);
        pulse2.load(in);
        saw.load(in);
    }

    private void clockIrq() {
        if (irqCount == 0xFF) {
            irqCount = irqLatch;
            irqLine = true;
        } else {
            irqCount++;
        }
    }

    private int remap(int address) {
        if (!swap) {
            return address;
        }
        return (address & ~3) | ((address & 1) << 1) | ((address & 2) >> 1);
    }

    private int prgOffset(int address) {
        int b16 = Math.max(1, prg.length / 0x4000);
        int b8 = Math.max(1, prg.length / 0x2000);
        if (address >= 0xE000) {
            return (b8 - 1) * 0x2000 + (address & 0x1FFF);
        }
        if (address >= 0xC000) {
            return (prg8 % b8) * 0x2000 + (address & 0x1FFF);
        }
        return (prg16 % b16) * 0x4000 + (address & 0x3FFF);
    }

    private static final class Pulse {
        int ctrl;
        int period;
        int timer;
        int pos;
        boolean on;

        void clock() {
            if (timer == 0) {
                timer = period;
                pos = (pos + 1) & 15;
            } else {
                timer--;
            }
        }

        int output() {
            if (!on) {
                return 0;
            }
            if ((ctrl & 0x80) != 0) {
                return ctrl & 0x0F;
            }
            int duty = ((ctrl >> 4) & 7) + 1;
            return pos < duty ? ctrl & 0x0F : 0;
        }

        void save(java.io.DataOutput out) throws java.io.IOException {
            out.writeInt(ctrl);
            out.writeInt(period);
            out.writeInt(timer);
            out.writeInt(pos);
            out.writeBoolean(on);
        }

        void load(java.io.DataInput in) throws java.io.IOException {
            ctrl = in.readInt();
            period = in.readInt();
            timer = in.readInt();
            pos = in.readInt();
            on = in.readBoolean();
        }
    }

    private static final class Saw {
        int rate;
        int period;
        int timer;
        int accum;
        int step;
        boolean on;

        void clock() {
            if (timer == 0) {
                timer = period;
                if (step == 6) {
                    step = 0;
                    accum = 0;
                } else {
                    step++;
                    accum = (accum + rate) & 0xFF;
                }
            } else {
                timer--;
            }
        }

        int output() {
            return on ? accum >> 3 : 0;
        }

        void save(java.io.DataOutput out) throws java.io.IOException {
            out.writeInt(rate);
            out.writeInt(period);
            out.writeInt(timer);
            out.writeInt(accum);
            out.writeInt(step);
            out.writeBoolean(on);
        }

        void load(java.io.DataInput in) throws java.io.IOException {
            rate = in.readInt();
            period = in.readInt();
            timer = in.readInt();
            accum = in.readInt();
            step = in.readInt();
            on = in.readBoolean();
        }
    }
}
