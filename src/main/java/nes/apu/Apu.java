package nes.apu;

/**
 * NTSC APU：方波×2、三角、噪声。DMC 本切片不发声。
 * 不碰声卡；每个 CPU cycle 推进一次，按 44100 Hz 产出采样。
 */
public final class Apu {
    public static final int SAMPLE_RATE = 44100;
    private static final int CPU_HZ = 1_789_773;
    private static final int[] LENGTH = {
        10, 254, 20, 2, 40, 4, 80, 6, 160, 8, 60, 10, 14, 12, 26, 14,
        12, 16, 24, 18, 48, 20, 96, 22, 192, 24, 72, 26, 16, 28, 32, 30
    };
    private static final int[] NOISE_PERIOD = {
        4, 8, 16, 32, 64, 96, 128, 160, 202, 254, 380, 508, 762, 1016, 2034, 4068
    };
    private static final int[][] DUTY = {
        {0, 0, 0, 0, 0, 0, 0, 1},
        {0, 0, 0, 0, 0, 0, 1, 1},
        {0, 0, 0, 0, 1, 1, 1, 1},
        {1, 1, 1, 1, 1, 1, 0, 0}
    };

    private final Pulse pulse1 = new Pulse(true);
    private final Pulse pulse2 = new Pulse(false);
    private final Triangle triangle = new Triangle();
    private final Noise noise = new Noise();
    private final short[] buffer = new short[SAMPLE_RATE * 2];
    private int bufferSize;
    private int samplePhase;
    private int frameCycle;
    private boolean fiveStep;
    private boolean irqInhibit;
    private boolean frameIrq;
    private boolean oddCycle;

    public void reset() {
        pulse1.reset();
        pulse2.reset();
        triangle.reset();
        noise.reset();
        bufferSize = 0;
        samplePhase = 0;
        frameCycle = 0;
        fiveStep = false;
        irqInhibit = false;
        frameIrq = false;
        oddCycle = false;
    }

    public void write(int address, int value) {
        value &= 0xFF;
        switch (address) {
            case 0x4000 -> pulse1.write0(value);
            case 0x4001 -> pulse1.write1(value);
            case 0x4002 -> pulse1.write2(value);
            case 0x4003 -> pulse1.write3(value);
            case 0x4004 -> pulse2.write0(value);
            case 0x4005 -> pulse2.write1(value);
            case 0x4006 -> pulse2.write2(value);
            case 0x4007 -> pulse2.write3(value);
            case 0x4008 -> triangle.write0(value);
            case 0x400A -> triangle.write2(value);
            case 0x400B -> triangle.write3(value);
            case 0x400C -> noise.write0(value);
            case 0x400E -> noise.write2(value);
            case 0x400F -> noise.write3(value);
            case 0x4015 -> {
                pulse1.setEnabled((value & 1) != 0);
                pulse2.setEnabled((value & 2) != 0);
                triangle.setEnabled((value & 4) != 0);
                noise.setEnabled((value & 8) != 0);
            }
            case 0x4017 -> {
                fiveStep = (value & 0x80) != 0;
                irqInhibit = (value & 0x40) != 0;
                if (irqInhibit) {
                    frameIrq = false;
                }
                frameCycle = 0;
                if (fiveStep) {
                    quarter();
                    half();
                }
            }
            default -> {
            }
        }
    }

    public int read4015() {
        int v = 0;
        if (pulse1.length > 0) {
            v |= 1;
        }
        if (pulse2.length > 0) {
            v |= 2;
        }
        if (triangle.length > 0) {
            v |= 4;
        }
        if (noise.length > 0) {
            v |= 8;
        }
        if (frameIrq) {
            v |= 0x40;
        }
        frameIrq = false;
        return v;
    }

    public boolean irqAsserted() {
        return frameIrq;
    }

    public void tick() {
        oddCycle = !oddCycle;
        if (oddCycle) {
            pulse1.clockTimer();
            pulse2.clockTimer();
            noise.clockTimer();
        }
        triangle.clockTimer();
        clockFrame();
        samplePhase += SAMPLE_RATE;
        if (samplePhase >= CPU_HZ) {
            samplePhase -= CPU_HZ;
            if (bufferSize < buffer.length) {
                buffer[bufferSize++] = (short) mix();
            }
        }
    }

    public short[] drain() {
        short[] out = new short[bufferSize];
        drainTo(out);
        return out;
    }

    /** 拷进调用方缓冲，热路径不分配。 */
    public int drainTo(short[] dest) {
        int n = Math.min(bufferSize, dest.length);
        System.arraycopy(buffer, 0, dest, 0, n);
        bufferSize = 0;
        return n;
    }

    private void clockFrame() {
        frameCycle++;
        if (!fiveStep) {
            if (frameCycle == 7457) {
                quarter();
            } else if (frameCycle == 14913) {
                quarter();
                half();
            } else if (frameCycle == 22371) {
                quarter();
            } else if (frameCycle == 29829) {
                quarter();
                half();
                if (!irqInhibit) {
                    frameIrq = true;
                }
            } else if (frameCycle >= 29830) {
                frameCycle = 0;
            }
        } else if (frameCycle == 7457) {
            quarter();
        } else if (frameCycle == 14913) {
            quarter();
            half();
        } else if (frameCycle == 22371) {
            quarter();
        } else if (frameCycle == 29829) {
            quarter();
            half();
        } else if (frameCycle >= 37282) {
            frameCycle = 0;
        }
    }

    private void quarter() {
        pulse1.envelope.clock();
        pulse2.envelope.clock();
        noise.envelope.clock();
        triangle.clockLinear();
    }

    private void half() {
        pulse1.clockLength();
        pulse2.clockLength();
        triangle.clockLength();
        noise.clockLength();
        pulse1.clockSweep();
        pulse2.clockSweep();
    }

    private int mix() {
        int p = pulse1.output() + pulse2.output();
        int t = triangle.output();
        int n = noise.output();
        double pulseOut = p == 0 ? 0 : 95.88 / (8128.0 / p + 100);
        double tnd = t / 8227.0 + n / 12241.0;
        double tndOut = tnd == 0 ? 0 : 159.79 / (1.0 / tnd + 100);
        return (int) ((pulseOut + tndOut) * 30000);
    }

    private static final class Envelope {
        boolean start;
        boolean loop;
        boolean constant;
        int period;
        int divider;
        int decay;

        void write(int value) {
            constant = (value & 0x10) != 0;
            loop = (value & 0x20) != 0;
            period = value & 0x0F;
        }

        void clock() {
            if (start) {
                start = false;
                decay = 15;
                divider = period;
                return;
            }
            if (divider == 0) {
                divider = period;
                if (decay > 0) {
                    decay--;
                } else if (loop) {
                    decay = 15;
                }
            } else {
                divider--;
            }
        }

        int volume() {
            return constant ? period : decay;
        }
    }

    private static final class Pulse {
        final boolean pulse1;
        final Envelope envelope = new Envelope();
        boolean enabled;
        boolean halt;
        int duty;
        int dutyPos;
        int timer;
        int period;
        int length;
        boolean sweepEnable;
        boolean sweepNegate;
        boolean sweepReload;
        int sweepPeriod;
        int sweepShift;
        int sweepDivider;

        Pulse(boolean pulse1) {
            this.pulse1 = pulse1;
        }

        void reset() {
            enabled = false;
            length = 0;
            period = 0;
            timer = 0;
        }

        void write0(int value) {
            duty = (value >> 6) & 3;
            halt = (value & 0x20) != 0;
            envelope.write(value);
        }

        void write1(int value) {
            sweepEnable = (value & 0x80) != 0;
            sweepPeriod = (value >> 4) & 7;
            sweepNegate = (value & 0x08) != 0;
            sweepShift = value & 7;
            sweepReload = true;
        }

        void write2(int value) {
            period = (period & 0x700) | value;
        }

        void write3(int value) {
            period = (period & 0xFF) | ((value & 7) << 8);
            if (enabled) {
                length = LENGTH[(value >> 3) & 0x1F];
            }
            dutyPos = 0;
            envelope.start = true;
            timer = period;
        }

        void setEnabled(boolean on) {
            enabled = on;
            if (!on) {
                length = 0;
            }
        }

        void clockTimer() {
            if (timer == 0) {
                timer = period;
                dutyPos = (dutyPos + 1) & 7;
            } else {
                timer--;
            }
        }

        void clockLength() {
            if (!halt && length > 0) {
                length--;
            }
        }

        void clockSweep() {
            if (sweepReload) {
                sweepDivider = sweepPeriod;
                sweepReload = false;
            } else if (sweepDivider == 0) {
                sweepDivider = sweepPeriod;
                if (sweepEnable && sweepShift > 0 && !sweepMute()) {
                    period = sweepTarget() & 0x7FF;
                }
            } else {
                sweepDivider--;
            }
        }

        int sweepTarget() {
            int change = period >> sweepShift;
            if (sweepNegate) {
                return pulse1 ? period - change - 1 : period - change;
            }
            return period + change;
        }

        boolean sweepMute() {
            return period < 8 || sweepTarget() > 0x7FF;
        }

        int output() {
            if (!enabled || length == 0 || sweepMute() || DUTY[duty][dutyPos] == 0) {
                return 0;
            }
            return envelope.volume();
        }
    }

    private static final class Triangle {
        boolean enabled;
        boolean control;
        boolean reloadFlag;
        int reload;
        int linear;
        int timer;
        int period;
        int length;
        int seq;

        void reset() {
            enabled = false;
            length = 0;
            linear = 0;
            period = 0;
        }

        void write0(int value) {
            control = (value & 0x80) != 0;
            reload = value & 0x7F;
        }

        void write2(int value) {
            period = (period & 0x700) | value;
        }

        void write3(int value) {
            period = (period & 0xFF) | ((value & 7) << 8);
            if (enabled) {
                length = LENGTH[(value >> 3) & 0x1F];
            }
            reloadFlag = true;
        }

        void setEnabled(boolean on) {
            enabled = on;
            if (!on) {
                length = 0;
            }
        }

        void clockTimer() {
            if (period < 2) {
                return;
            }
            if (timer == 0) {
                timer = period;
                if (linear > 0 && length > 0) {
                    seq = (seq + 1) & 31;
                }
            } else {
                timer--;
            }
        }

        void clockLinear() {
            if (reloadFlag) {
                linear = reload;
            } else if (linear > 0) {
                linear--;
            }
            if (!control) {
                reloadFlag = false;
            }
        }

        void clockLength() {
            if (!control && length > 0) {
                length--;
            }
        }

        int output() {
            if (!enabled || length == 0 || linear == 0 || period < 2) {
                return 0;
            }
            return seq < 16 ? 15 - seq : seq - 16;
        }
    }

    private static final class Noise {
        final Envelope envelope = new Envelope();
        boolean enabled;
        boolean halt;
        boolean shortMode;
        int timer;
        int periodIndex;
        int length;
        int lfsr = 1;

        void reset() {
            enabled = false;
            length = 0;
            lfsr = 1;
        }

        void write0(int value) {
            halt = (value & 0x20) != 0;
            envelope.write(value);
        }

        void write2(int value) {
            shortMode = (value & 0x80) != 0;
            periodIndex = value & 0x0F;
        }

        void write3(int value) {
            if (enabled) {
                length = LENGTH[(value >> 3) & 0x1F];
            }
            envelope.start = true;
        }

        void setEnabled(boolean on) {
            enabled = on;
            if (!on) {
                length = 0;
            }
        }

        void clockTimer() {
            if (timer == 0) {
                timer = NOISE_PERIOD[periodIndex];
                int bit = shortMode ? 6 : 1;
                int feedback = (lfsr & 1) ^ ((lfsr >> bit) & 1);
                lfsr = (lfsr >> 1) | (feedback << 14);
            } else {
                timer--;
            }
        }

        void clockLength() {
            if (!halt && length > 0) {
                length--;
            }
        }

        int output() {
            if (!enabled || length == 0 || (lfsr & 1) != 0) {
                return 0;
            }
            return envelope.volume();
        }
    }
}
