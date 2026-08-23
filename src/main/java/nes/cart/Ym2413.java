package nes.cart;

/**
 * YM2413 / VRC7 OPLL：log-sin+exp、ADSR、KSL、AM/VIB、反馈、9 路与节奏。
 */
public final class Ym2413 {
    private final boolean vrc7;
    private final byte[] user = new byte[8];
    private final int[] fnum = new int[9];
    private final int[] block = new int[9];
    private final int[] inst = new int[9];
    private final int[] vol = new int[9];
    private final boolean[] key = new boolean[9];
    private final boolean[] sus = new boolean[9];
    private final int[] phase = new int[18];
    private final int[] env = new int[18];
    private final int[] eg = new int[18];
    private final int[] fbOut = new int[9];
    private int rhythm;
    private int lfo;
    private int chipAcc;
    private int clk;
    private int out;
    private int noise = 1;
    private final int[] pgFrac = new int[18];
    private final int[] slotOut = new int[18];

    public Ym2413(boolean vrc7) {
        this.vrc7 = vrc7;
        for (int i = 0; i < env.length; i++) {
            env[i] = 127;
            eg[i] = 3;
        }
    }

    public void write(int address, int value) {
        address &= 0x3F;
        value &= 0xFF;
        if (address < 8) {
            user[address] = (byte) value;
            return;
        }
        if (!vrc7 && address == 0x0E) {
            boolean was = (rhythm & 0x20) != 0;
            rhythm = value;
            if (!was && (value & 0x20) != 0) {
                keyRhythm();
            }
            if (was && (value & 0x20) == 0) {
                for (int ch = 6; ch < 9; ch++) {
                    keyOn(ch, false);
                }
            }
            return;
        }
        int ch = address & 0x0F;
        if (ch > 8 || (vrc7 && ch > 5)) {
            return;
        }
        if (address >= 0x10 && address <= 0x18) {
            fnum[ch] = (fnum[ch] & 0x100) | value;
            return;
        }
        if (address >= 0x20 && address <= 0x28) {
            fnum[ch] = (fnum[ch] & 0xFF) | ((value & 1) << 8);
            block[ch] = (value >> 1) & 7;
            sus[ch] = (value & 0x20) != 0;
            keyOn(ch, (value & 0x10) != 0);
            return;
        }
        if (address >= 0x30 && address <= 0x38) {
            inst[ch] = (value >> 4) & 0x0F;
            vol[ch] = value & 0x0F;
        }
    }

    public void tick(int cpuHz) {
        int chipHz = vrc7 ? 3_600_000 : 3_579_545;
        chipAcc += chipHz;
        while (chipAcc >= cpuHz) {
            chipAcc -= cpuHz;
            clockChip();
        }
    }

    public int output() {
        return out;
    }

    void save(java.io.DataOutput o) throws java.io.IOException {
        o.write(user);
        for (int i = 0; i < 9; i++) {
            o.writeInt(fnum[i]);
            o.writeInt(block[i]);
            o.writeInt(inst[i]);
            o.writeInt(vol[i]);
            o.writeBoolean(key[i]);
            o.writeBoolean(sus[i]);
        }
        for (int p : phase) {
            o.writeInt(p);
        }
        for (int e : env) {
            o.writeInt(e);
        }
        for (int g : eg) {
            o.writeInt(g);
        }
        for (int f : fbOut) {
            o.writeInt(f);
        }
        o.writeInt(rhythm);
        o.writeInt(lfo);
        o.writeInt(chipAcc);
        o.writeInt(clk);
        o.writeInt(out);
        o.writeInt(noise);
        for (int f : pgFrac) {
            o.writeInt(f);
        }
    }

    void load(java.io.DataInput i) throws java.io.IOException {
        i.readFully(user);
        for (int n = 0; n < 9; n++) {
            fnum[n] = i.readInt();
            block[n] = i.readInt();
            inst[n] = i.readInt();
            vol[n] = i.readInt();
            key[n] = i.readBoolean();
            sus[n] = i.readBoolean();
        }
        for (int n = 0; n < phase.length; n++) {
            phase[n] = i.readInt();
        }
        for (int n = 0; n < env.length; n++) {
            env[n] = i.readInt();
        }
        for (int n = 0; n < eg.length; n++) {
            eg[n] = i.readInt();
        }
        for (int n = 0; n < fbOut.length; n++) {
            fbOut[n] = i.readInt();
        }
        rhythm = i.readInt();
        lfo = i.readInt();
        chipAcc = i.readInt();
        clk = i.readInt();
        out = i.readInt();
        noise = i.readInt();
        for (int n = 0; n < pgFrac.length; n++) {
            pgFrac[n] = i.readInt();
        }
    }

    private void clockChip() {
        advanceSlot(clk % 18);
        clk++;
        if (clk == 72) {
            clk = 0;
            mix();
        }
    }

    private void advanceSlot(int s) {
        int ch = s >> 1;
        int op = s & 1;
        if (vrc7 && ch > 5) {
            return;
        }
        byte[] p = patch(ch);
        int r0 = p[op] & 0xFF;
        int r4 = p[4 + op] & 0xFF;
        int r6 = p[6 + op] & 0xFF;
        int ml = ML[r0 & 0x0F];
        int vib = (r0 & 0x40) != 0 ? (((lfo >> 3) & 7) - 4) : 0;
        int inc = ((fnum[ch] + vib) * ml) << block[ch];
        int add = inc + pgFrac[s];
        pgFrac[s] = add & 3;
        phase[s] += add >> 2;
        if (clk < 18) {
            clockEg(s, r4, r6, key[ch], sus[ch], (r0 & 0x20) != 0, block[ch]);
        }
        int n = (!vrc7 && (rhythm & 0x20) != 0 && ch >= 7) ? (noise & 1) * 0x200 : 0;
        int pmIn = op == 0 ? fb(ch, p) : fbOut[ch] << 1;
        slotOut[s] = operator(ch, op, p, pmIn, n);
        if (op == 0) {
            fbOut[ch] = slotOut[s];
        }
    }

    private void mix() {
        lfo++;
        noise = (noise >> 1) ^ ((noise & 1) != 0 ? 0x800302 : 0);
        int sum = 0;
        int last = vrc7 ? 6 : 9;
        boolean rh = !vrc7 && (rhythm & 0x20) != 0;
        for (int ch = 0; ch < last; ch++) {
            if (rh && ch == 6) {
                sum += (rhythm & 0x10) != 0 ? slotOut[13] : 0;
            } else if (rh && ch == 7) {
                sum += ((rhythm & 1) != 0 ? slotOut[14] : 0) + ((rhythm & 8) != 0 ? slotOut[15] : 0);
            } else if (rh && ch == 8) {
                sum += ((rhythm & 4) != 0 ? slotOut[16] : 0) + ((rhythm & 2) != 0 ? slotOut[17] : 0);
            } else {
                sum += slotOut[ch * 2 + 1];
            }
        }
        out = Math.min(127, Math.abs(sum) >> 6);
    }

    private void keyOn(int ch, boolean on) {
        if (on && !key[ch]) {
            phase[ch * 2] = 0;
            phase[ch * 2 + 1] = 0;
            eg[ch * 2] = 0;
            eg[ch * 2 + 1] = 0;
            byte[] p = patch(ch);
            env[ch * 2] = ((p[4] >> 4) & 0x0F) >= 14 ? 0 : 127;
            env[ch * 2 + 1] = ((p[5] >> 4) & 0x0F) >= 14 ? 0 : 127;
        }
        if (!on && key[ch]) {
            eg[ch * 2] = 3;
            eg[ch * 2 + 1] = 3;
        }
        key[ch] = on;
    }

    private void keyRhythm() {
        keyOn(6, (rhythm & 0x10) != 0);
        keyOn(7, (rhythm & 0x01) != 0 || (rhythm & 0x08) != 0);
        keyOn(8, (rhythm & 0x04) != 0 || (rhythm & 0x02) != 0);
    }

    private int operator(int ch, int op, byte[] p, int pmIn, int phaseOr) {
        int idx = ch * 2 + op;
        int r0 = p[op] & 0xFF;
        int r2 = p[2 + op] & 0xFF;
        int ksl = ksl((r2 >> 6) & 3, block[ch], fnum[ch]);
        int tl = op == 0 ? (r2 & 0x3F) << 1 : vol[ch] << 3;
        int att = (env[idx] << 1) + tl + ksl + ((r0 & 0x80) != 0 ? am() : 0);
        int ph = (phase[idx] >> 9) + pmIn + phaseOr;
        boolean half = op == 0 ? (p[3] & 8) != 0 : (p[3] & 0x10) != 0;
        return wave(ph, att, half);
    }

    private void clockEg(int idx, int r4, int r6, boolean on, boolean sustain, boolean egType, int blk) {
        int ksr = blk >> (egType ? 0 : 2);
        if (eg[idx] == 0) {
            int ar = (r4 >> 4) & 0x0F;
            env[idx] = ar == 0 ? 127 : Math.max(0, env[idx] - Math.max(1, ar + ksr));
            if (env[idx] == 0) {
                eg[idx] = 1;
            }
            return;
        }
        if (eg[idx] == 1) {
            int dr = r4 & 0x0F;
            int sl = (r6 >> 4) & 0x0F;
            env[idx] = Math.min(127, env[idx] + Math.max(1, dr + ksr));
            if (env[idx] >= sl * 8) {
                eg[idx] = 2;
            }
            return;
        }
        if (eg[idx] == 2 && !egType && on) {
            return;
        }
        if (!on || eg[idx] == 3) {
            int rr = on && sustain ? 5 : r6 & 0x0F;
            env[idx] = Math.min(127, env[idx] + Math.max(1, rr + ksr));
            eg[idx] = 3;
        }
    }

    private int fb(int ch, byte[] p) {
        int fb = p[3] & 7;
        if (fb == 0) {
            return 0;
        }
        return fbOut[ch] >> (8 - fb);
    }

    private byte[] patch(int ch) {
        int n = inst[ch];
        if (n == 0) {
            return user;
        }
        return vrc7 ? VRC7[n] : YM[n];
    }

    private int am() {
        int t = lfo & 63;
        return t < 32 ? t >> 2 : (63 - t) >> 2;
    }

    private static int ksl(int level, int block, int fnum) {
        if (level == 0) {
            return 0;
        }
        int att = Math.max(0, block * 8 + (fnum >> 5) - 16);
        return att << (level - 1);
    }

    private static int wave(int phase, int att, boolean half) {
        if (att >= 128) {
            return 0;
        }
        int p = phase & 1023;
        if (half && p >= 512) {
            return 0;
        }
        boolean neg = p >= 512;
        p &= 511;
        if (p >= 256) {
            p = 511 - p;
        }
        int ls = LOGSIN[p] + (att << 4);
        int s = exp(ls);
        return neg ? -s : s;
    }

    private static int exp(int ls) {
        int shift = ls >> 8;
        int frac = ls & 255;
        if (shift > 13) {
            return 0;
        }
        return EXP[frac] >> shift;
    }

    private static final int[] ML = {1, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 20, 24, 24, 30, 30};
    private static final int[] LOGSIN = new int[256];
    private static final int[] EXP = new int[256];
    private static final byte[][] VRC7 = instVrc7();
    private static final byte[][] YM = instYm();

    static {
        for (int i = 0; i < 256; i++) {
            LOGSIN[i] = (int) Math.round(-Math.log(Math.sin((i + 0.5) * Math.PI / 512.0)) / Math.log(2) * 256);
            EXP[i] = (int) Math.round(Math.pow(2, (255 - i) / 256.0) * 1024);
        }
    }

    private static byte[][] instVrc7() {
        return new byte[][] {
                {0, 0, 0, 0, 0, 0, 0, 0},
                {0x03, 0x21, 0x05, 0x06, (byte) 0xE8, (byte) 0x81, 0x42, 0x27},
                {0x13, 0x41, 0x14, 0x0D, (byte) 0xD8, (byte) 0xF6, 0x23, 0x12},
                {0x11, 0x11, 0x08, 0x08, (byte) 0xFA, (byte) 0xB2, 0x20, 0x12},
                {0x31, 0x61, 0x0C, 0x07, (byte) 0xA8, 0x64, 0x61, 0x27},
                {0x32, 0x21, 0x1E, 0x06, (byte) 0xE1, 0x76, 0x01, 0x28},
                {0x02, 0x01, 0x06, 0x00, (byte) 0xA3, (byte) 0xE2, (byte) 0xF4, (byte) 0xF4},
                {0x21, 0x61, 0x1D, 0x07, (byte) 0x82, (byte) 0x81, 0x11, 0x07},
                {0x23, 0x21, 0x22, 0x17, (byte) 0xA2, 0x72, 0x01, 0x17},
                {0x35, 0x11, 0x25, 0x00, 0x40, 0x73, 0x72, 0x01},
                {(byte) 0xB5, 0x01, 0x0F, 0x0F, (byte) 0xA8, (byte) 0xA5, 0x51, 0x02},
                {0x17, (byte) 0xC1, 0x24, 0x07, (byte) 0xF8, (byte) 0xF8, 0x22, 0x12},
                {0x71, 0x23, 0x11, 0x06, 0x65, 0x74, 0x18, 0x16},
                {0x01, 0x02, (byte) 0xD3, 0x05, (byte) 0xC9, (byte) 0x95, 0x03, 0x12},
                {0x61, 0x63, 0x0C, 0x00, (byte) 0x94, (byte) 0xC0, 0x33, (byte) 0xF6},
                {0x21, 0x72, 0x0D, 0x00, (byte) 0xC1, (byte) 0xD5, 0x56, 0x06}
        };
    }

    private static byte[][] instYm() {
        return new byte[][] {
                {0, 0, 0, 0, 0, 0, 0, 0},
                {0x71, 0x61, 0x1E, 0x17, (byte) 0xD0, 0x78, 0x00, 0x17},
                {0x13, 0x41, 0x1A, 0x0D, (byte) 0xD8, (byte) 0xF7, 0x23, 0x13},
                {0x13, 0x01, (byte) 0x99, 0x00, (byte) 0xF2, (byte) 0xC4, 0x11, 0x23},
                {0x31, 0x61, 0x0E, 0x07, (byte) 0xA8, 0x64, 0x70, 0x27},
                {0x32, 0x21, 0x1E, 0x06, (byte) 0xE0, 0x76, 0x00, 0x28},
                {0x31, 0x22, 0x16, 0x05, (byte) 0xE0, 0x71, 0x00, 0x18},
                {0x21, 0x61, 0x1D, 0x07, (byte) 0x82, (byte) 0x81, 0x10, 0x07},
                {0x23, 0x21, 0x2D, 0x14, (byte) 0xA2, 0x72, 0x00, 0x07},
                {0x61, 0x61, 0x1B, 0x06, 0x64, 0x65, 0x10, 0x17},
                {0x41, 0x61, 0x0B, 0x18, (byte) 0x85, (byte) 0xF0, 0x70, 0x07},
                {0x13, 0x01, (byte) 0x83, 0x11, (byte) 0xFA, (byte) 0xE4, 0x10, 0x04},
                {0x17, (byte) 0xC1, 0x24, 0x07, (byte) 0xF8, (byte) 0xF8, 0x22, 0x12},
                {0x61, 0x50, 0x0C, 0x05, (byte) 0xD2, (byte) 0xF5, (byte) 0x40, 0x42},
                {0x01, 0x01, 0x55, 0x03, (byte) 0xC9, (byte) 0x95, 0x03, 0x02},
                {0x61, 0x41, (byte) 0x89, 0x03, (byte) 0xF1, (byte) 0xE4, (byte) 0x40, 0x13}
        };
    }
}
