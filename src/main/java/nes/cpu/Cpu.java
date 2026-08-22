package nes.cpu;

import nes.bus.CpuMemory;

/** 6502（2A03：ADC/SBC 不做 BCD）。按指令步进，返回周期数。 */
public final class Cpu {
    private static final int C = 0x01;
    private static final int Z = 0x02;
    private static final int I = 0x04;
    private static final int D = 0x08;
    private static final int B = 0x10;
    private static final int U = 0x20;
    private static final int V = 0x40;
    private static final int N = 0x80;

    private final CpuMemory bus;
    int a;
    int x;
    int y;
    int sp;
    int pc;
    int p;
    private boolean nmi;
    private boolean irq;
    private int stall;
    private long cycles;

    public Cpu(CpuMemory bus) {
        this.bus = bus;
    }

    public void reset() {
        a = 0;
        x = 0;
        y = 0;
        sp = 0xFD;
        p = U | I;
        pc = read16(0xFFFC);
        nmi = false;
        irq = false;
        stall = 0;
        cycles = 7;
    }

    public void nmi() {
        nmi = true;
    }

    public void irq(boolean asserted) {
        irq = asserted;
    }

    public void stall(int extra) {
        stall += extra;
    }

    public long cycles() {
        return cycles;
    }

    public int step() {
        if (stall > 0) {
            stall--;
            cycles++;
            return 1;
        }
        if (nmi) {
            nmi = false;
            return interrupt(0xFFFA, false);
        }
        if (irq && (p & I) == 0) {
            return interrupt(0xFFFE, false);
        }
        int op = fetch();
        int used = execute(op);
        cycles += used;
        return used;
    }

    private int execute(int op) {
        switch (op) {
            case 0x00 -> {
                return brk();
            }
            case 0x01 -> {
                ora(izx());
                return 6;
            }
            case 0x05 -> {
                ora(zp());
                return 3;
            }
            case 0x06 -> {
                aslMem(zp());
                return 5;
            }
            case 0x08 -> {
                push(p | B | U);
                return 3;
            }
            case 0x09 -> {
                ora(imm());
                return 2;
            }
            case 0x0A -> {
                a = aslVal(a);
                return 2;
            }
            case 0x0D -> {
                ora(abs_());
                return 4;
            }
            case 0x0E -> {
                aslMem(abs_());
                return 6;
            }
            case 0x10 -> {
                return branch((p & N) == 0);
            }
            case 0x11 -> {
                return 5 + oraIzY();
            }
            case 0x15 -> {
                ora(zpx());
                return 4;
            }
            case 0x16 -> {
                aslMem(zpx());
                return 6;
            }
            case 0x18 -> {
                p &= ~C;
                return 2;
            }
            case 0x19 -> {
                return 4 + oraAbY();
            }
            case 0x1D -> {
                return 4 + oraAbX();
            }
            case 0x1E -> {
                aslMem(abxWrite());
                return 7;
            }
            case 0x20 -> {
                return jsr();
            }
            case 0x21 -> {
                and(izx());
                return 6;
            }
            case 0x24 -> {
                bit(zp());
                return 3;
            }
            case 0x25 -> {
                and(zp());
                return 3;
            }
            case 0x26 -> {
                rolMem(zp());
                return 5;
            }
            case 0x28 -> {
                p = pop() | U;
                return 4;
            }
            case 0x29 -> {
                and(imm());
                return 2;
            }
            case 0x2A -> {
                a = rolVal(a);
                return 2;
            }
            case 0x2C -> {
                bit(abs_());
                return 4;
            }
            case 0x2D -> {
                and(abs_());
                return 4;
            }
            case 0x2E -> {
                rolMem(abs_());
                return 6;
            }
            case 0x30 -> {
                return branch((p & N) != 0);
            }
            case 0x31 -> {
                return 5 + andIzY();
            }
            case 0x35 -> {
                and(zpx());
                return 4;
            }
            case 0x36 -> {
                rolMem(zpx());
                return 6;
            }
            case 0x38 -> {
                p |= C;
                return 2;
            }
            case 0x39 -> {
                return 4 + andAbY();
            }
            case 0x3D -> {
                return 4 + andAbX();
            }
            case 0x3E -> {
                rolMem(abxWrite());
                return 7;
            }
            case 0x40 -> {
                return rti();
            }
            case 0x41 -> {
                eor(izx());
                return 6;
            }
            case 0x45 -> {
                eor(zp());
                return 3;
            }
            case 0x46 -> {
                lsrMem(zp());
                return 5;
            }
            case 0x48 -> {
                push(a);
                return 3;
            }
            case 0x49 -> {
                eor(imm());
                return 2;
            }
            case 0x4A -> {
                a = lsrVal(a);
                return 2;
            }
            case 0x4C -> {
                pc = abs_();
                return 3;
            }
            case 0x4D -> {
                eor(abs_());
                return 4;
            }
            case 0x4E -> {
                lsrMem(abs_());
                return 6;
            }
            case 0x50 -> {
                return branch((p & V) == 0);
            }
            case 0x51 -> {
                return 5 + eorIzY();
            }
            case 0x55 -> {
                eor(zpx());
                return 4;
            }
            case 0x56 -> {
                lsrMem(zpx());
                return 6;
            }
            case 0x58 -> {
                p &= ~I;
                return 2;
            }
            case 0x59 -> {
                return 4 + eorAbY();
            }
            case 0x5D -> {
                return 4 + eorAbX();
            }
            case 0x5E -> {
                lsrMem(abxWrite());
                return 7;
            }
            case 0x60 -> {
                return rts();
            }
            case 0x61 -> {
                adc(read(izx()));
                return 6;
            }
            case 0x65 -> {
                adc(read(zp()));
                return 3;
            }
            case 0x66 -> {
                rorMem(zp());
                return 5;
            }
            case 0x68 -> {
                a = pop();
                zn(a);
                return 4;
            }
            case 0x69 -> {
                adc(read(imm()));
                return 2;
            }
            case 0x6A -> {
                a = rorVal(a);
                return 2;
            }
            case 0x6C -> {
                pc = jmpInd();
                return 5;
            }
            case 0x6D -> {
                adc(read(abs_()));
                return 4;
            }
            case 0x6E -> {
                rorMem(abs_());
                return 6;
            }
            case 0x70 -> {
                return branch((p & V) != 0);
            }
            case 0x71 -> {
                return 5 + adcIzY();
            }
            case 0x75 -> {
                adc(read(zpx()));
                return 4;
            }
            case 0x76 -> {
                rorMem(zpx());
                return 6;
            }
            case 0x78 -> {
                p |= I;
                return 2;
            }
            case 0x79 -> {
                return 4 + adcAbY();
            }
            case 0x7D -> {
                return 4 + adcAbX();
            }
            case 0x7E -> {
                rorMem(abxWrite());
                return 7;
            }
            case 0x81 -> {
                write(izx(), a);
                return 6;
            }
            case 0x84 -> {
                write(zp(), y);
                return 3;
            }
            case 0x85 -> {
                write(zp(), a);
                return 3;
            }
            case 0x86 -> {
                write(zp(), x);
                return 3;
            }
            case 0x88 -> {
                y = (y - 1) & 0xFF;
                zn(y);
                return 2;
            }
            case 0x8A -> {
                a = x;
                zn(a);
                return 2;
            }
            case 0x8C -> {
                write(abs_(), y);
                return 4;
            }
            case 0x8D -> {
                write(abs_(), a);
                return 4;
            }
            case 0x8E -> {
                write(abs_(), x);
                return 4;
            }
            case 0x90 -> {
                return branch((p & C) == 0);
            }
            case 0x91 -> {
                write(izyWrite(), a);
                return 6;
            }
            case 0x94 -> {
                write(zpx(), y);
                return 4;
            }
            case 0x95 -> {
                write(zpx(), a);
                return 4;
            }
            case 0x96 -> {
                write(zpy(), x);
                return 4;
            }
            case 0x98 -> {
                a = y;
                zn(a);
                return 2;
            }
            case 0x99 -> {
                write(abyWrite(), a);
                return 5;
            }
            case 0x9A -> {
                sp = x;
                return 2;
            }
            case 0x9D -> {
                write(abxWrite(), a);
                return 5;
            }
            case 0xA0 -> {
                y = read(imm());
                zn(y);
                return 2;
            }
            case 0xA1 -> {
                a = read(izx());
                zn(a);
                return 6;
            }
            case 0xA2 -> {
                x = read(imm());
                zn(x);
                return 2;
            }
            case 0xA4 -> {
                y = read(zp());
                zn(y);
                return 3;
            }
            case 0xA5 -> {
                a = read(zp());
                zn(a);
                return 3;
            }
            case 0xA6 -> {
                x = read(zp());
                zn(x);
                return 3;
            }
            case 0xA8 -> {
                y = a;
                zn(y);
                return 2;
            }
            case 0xA9 -> {
                a = read(imm());
                zn(a);
                return 2;
            }
            case 0xAA -> {
                x = a;
                zn(x);
                return 2;
            }
            case 0xAC -> {
                y = read(abs_());
                zn(y);
                return 4;
            }
            case 0xAD -> {
                a = read(abs_());
                zn(a);
                return 4;
            }
            case 0xAE -> {
                x = read(abs_());
                zn(x);
                return 4;
            }
            case 0xB0 -> {
                return branch((p & C) != 0);
            }
            case 0xB1 -> {
                return 5 + ldaIzY();
            }
            case 0xB4 -> {
                y = read(zpx());
                zn(y);
                return 4;
            }
            case 0xB5 -> {
                a = read(zpx());
                zn(a);
                return 4;
            }
            case 0xB6 -> {
                x = read(zpy());
                zn(x);
                return 4;
            }
            case 0xB8 -> {
                p &= ~V;
                return 2;
            }
            case 0xB9 -> {
                return 4 + ldaAbY();
            }
            case 0xBA -> {
                x = sp;
                zn(x);
                return 2;
            }
            case 0xBC -> {
                return 4 + ldyAbX();
            }
            case 0xBD -> {
                return 4 + ldaAbX();
            }
            case 0xBE -> {
                return 4 + ldxAbY();
            }
            case 0xC0 -> {
                cmp(y, read(imm()));
                return 2;
            }
            case 0xC1 -> {
                cmp(a, read(izx()));
                return 6;
            }
            case 0xC4 -> {
                cmp(y, read(zp()));
                return 3;
            }
            case 0xC5 -> {
                cmp(a, read(zp()));
                return 3;
            }
            case 0xC6 -> {
                dec(zp());
                return 5;
            }
            case 0xC8 -> {
                y = (y + 1) & 0xFF;
                zn(y);
                return 2;
            }
            case 0xC9 -> {
                cmp(a, read(imm()));
                return 2;
            }
            case 0xCA -> {
                x = (x - 1) & 0xFF;
                zn(x);
                return 2;
            }
            case 0xCC -> {
                cmp(y, read(abs_()));
                return 4;
            }
            case 0xCD -> {
                cmp(a, read(abs_()));
                return 4;
            }
            case 0xCE -> {
                dec(abs_());
                return 6;
            }
            case 0xD0 -> {
                return branch((p & Z) == 0);
            }
            case 0xD1 -> {
                return 5 + cmpIzY();
            }
            case 0xD5 -> {
                cmp(a, read(zpx()));
                return 4;
            }
            case 0xD6 -> {
                dec(zpx());
                return 6;
            }
            case 0xD8 -> {
                p &= ~D;
                return 2;
            }
            case 0xD9 -> {
                return 4 + cmpAbY();
            }
            case 0xDD -> {
                return 4 + cmpAbX();
            }
            case 0xDE -> {
                dec(abxWrite());
                return 7;
            }
            case 0xE0 -> {
                cmp(x, read(imm()));
                return 2;
            }
            case 0xE1 -> {
                sbc(read(izx()));
                return 6;
            }
            case 0xE4 -> {
                cmp(x, read(zp()));
                return 3;
            }
            case 0xE5 -> {
                sbc(read(zp()));
                return 3;
            }
            case 0xE6 -> {
                inc(zp());
                return 5;
            }
            case 0xE8 -> {
                x = (x + 1) & 0xFF;
                zn(x);
                return 2;
            }
            case 0xE9 -> {
                sbc(read(imm()));
                return 2;
            }
            case 0xEA -> {
                return 2;
            }
            case 0xEC -> {
                cmp(x, read(abs_()));
                return 4;
            }
            case 0xED -> {
                sbc(read(abs_()));
                return 4;
            }
            case 0xEE -> {
                inc(abs_());
                return 6;
            }
            case 0xF0 -> {
                return branch((p & Z) != 0);
            }
            case 0xF1 -> {
                return 5 + sbcIzY();
            }
            case 0xF5 -> {
                sbc(read(zpx()));
                return 4;
            }
            case 0xF6 -> {
                inc(zpx());
                return 6;
            }
            case 0xF8 -> {
                p |= D;
                return 2;
            }
            case 0xF9 -> {
                return 4 + sbcAbY();
            }
            case 0xFD -> {
                return 4 + sbcAbX();
            }
            case 0xFE -> {
                inc(abxWrite());
                return 7;
            }
            case 0x04, 0x44, 0x64 -> {
                zp();
                return 3;
            }
            case 0x14, 0x34, 0x54, 0x74, 0xD4, 0xF4 -> {
                zpx();
                return 4;
            }
            case 0x80, 0x82, 0x89, 0xC2, 0xE2 -> {
                imm();
                return 2;
            }
            case 0x1A, 0x3A, 0x5A, 0x7A, 0xDA, 0xFA -> {
                return 2;
            }
            case 0x0C -> {
                abs_();
                return 4;
            }
            case 0x1C, 0x3C, 0x5C, 0x7C, 0xDC, 0xFC -> {
                return 4 + nopAbX();
            }
            default -> throw new IllegalStateException(
                    "未实现的 6502 操作码 $" + Integer.toHexString(op) + " @ $" + Integer.toHexString(pc - 1));
        }
    }

    private int interrupt(int vector, boolean brk) {
        push(pc >> 8);
        push(pc);
        push(brk ? (p | B | U) : ((p & ~B) | U));
        p |= I;
        pc = read16(vector);
        cycles += 7;
        return 7;
    }

    private int brk() {
        fetch();
        return interrupt(0xFFFE, true);
    }

    private int jsr() {
        int dest = abs_();
        int ret = (pc - 1) & 0xFFFF;
        push(ret >> 8);
        push(ret);
        pc = dest;
        return 6;
    }

    private int rti() {
        p = pop() | U;
        pc = pop();
        pc |= pop() << 8;
        return 6;
    }

    private int rts() {
        pc = pop();
        pc |= pop() << 8;
        pc = (pc + 1) & 0xFFFF;
        return 6;
    }

    private int jmpInd() {
        int ptr = abs_();
        int lo = read(ptr);
        int hi = read((ptr & 0xFF00) | ((ptr + 1) & 0xFF));
        return lo | (hi << 8);
    }

    private int branch(boolean take) {
        int off = (byte) fetch();
        if (!take) {
            return 2;
        }
        int old = pc;
        pc = (pc + off) & 0xFFFF;
        return 3 + (((old ^ pc) & 0x100) != 0 ? 1 : 0);
    }

    private void ora(int addr) {
        a |= read(addr);
        zn(a);
    }

    private int oraIzY() {
        int[] r = izy();
        ora(r[0]);
        return r[1];
    }

    private int oraAbX() {
        int[] r = abx();
        ora(r[0]);
        return r[1];
    }

    private int oraAbY() {
        int[] r = aby();
        ora(r[0]);
        return r[1];
    }

    private void and(int addr) {
        a &= read(addr);
        zn(a);
    }

    private int andIzY() {
        int[] r = izy();
        and(r[0]);
        return r[1];
    }

    private int andAbX() {
        int[] r = abx();
        and(r[0]);
        return r[1];
    }

    private int andAbY() {
        int[] r = aby();
        and(r[0]);
        return r[1];
    }

    private void eor(int addr) {
        a ^= read(addr);
        zn(a);
    }

    private int eorIzY() {
        int[] r = izy();
        eor(r[0]);
        return r[1];
    }

    private int eorAbX() {
        int[] r = abx();
        eor(r[0]);
        return r[1];
    }

    private int eorAbY() {
        int[] r = aby();
        eor(r[0]);
        return r[1];
    }

    private int adcIzY() {
        int[] r = izy();
        adc(read(r[0]));
        return r[1];
    }

    private int adcAbX() {
        int[] r = abx();
        adc(read(r[0]));
        return r[1];
    }

    private int adcAbY() {
        int[] r = aby();
        adc(read(r[0]));
        return r[1];
    }

    private int sbcIzY() {
        int[] r = izy();
        sbc(read(r[0]));
        return r[1];
    }

    private int sbcAbX() {
        int[] r = abx();
        sbc(read(r[0]));
        return r[1];
    }

    private int sbcAbY() {
        int[] r = aby();
        sbc(read(r[0]));
        return r[1];
    }

    private int ldaIzY() {
        int[] r = izy();
        a = read(r[0]);
        zn(a);
        return r[1];
    }

    private int ldaAbX() {
        int[] r = abx();
        a = read(r[0]);
        zn(a);
        return r[1];
    }

    private int ldaAbY() {
        int[] r = aby();
        a = read(r[0]);
        zn(a);
        return r[1];
    }

    private int ldxAbY() {
        int[] r = aby();
        x = read(r[0]);
        zn(x);
        return r[1];
    }

    private int ldyAbX() {
        int[] r = abx();
        y = read(r[0]);
        zn(y);
        return r[1];
    }

    private int cmpIzY() {
        int[] r = izy();
        cmp(a, read(r[0]));
        return r[1];
    }

    private int cmpAbX() {
        int[] r = abx();
        cmp(a, read(r[0]));
        return r[1];
    }

    private int cmpAbY() {
        int[] r = aby();
        cmp(a, read(r[0]));
        return r[1];
    }

    private int nopAbX() {
        return abx()[1];
    }

    private void bit(int addr) {
        int v = read(addr);
        p = (p & ~(Z | V | N)) | (v & (V | N)) | ((a & v) == 0 ? Z : 0);
    }

    private void adc(int v) {
        int sum = a + v + (p & C);
        setC(sum > 0xFF);
        setV(((a ^ sum) & (v ^ sum) & 0x80) != 0);
        a = sum & 0xFF;
        zn(a);
    }

    private void sbc(int v) {
        adc(v ^ 0xFF);
    }

    private void cmp(int left, int right) {
        int d = left - right;
        setC(left >= right);
        zn(d & 0xFF);
    }

    private int aslVal(int v) {
        setC((v & 0x80) != 0);
        v = (v << 1) & 0xFF;
        zn(v);
        return v;
    }

    private void aslMem(int addr) {
        write(addr, aslVal(read(addr)));
    }

    private int lsrVal(int v) {
        setC((v & 1) != 0);
        v >>= 1;
        zn(v);
        return v;
    }

    private void lsrMem(int addr) {
        write(addr, lsrVal(read(addr)));
    }

    private int rolVal(int v) {
        int c = p & C;
        setC((v & 0x80) != 0);
        v = ((v << 1) | c) & 0xFF;
        zn(v);
        return v;
    }

    private void rolMem(int addr) {
        write(addr, rolVal(read(addr)));
    }

    private int rorVal(int v) {
        int c = p & C;
        setC((v & 1) != 0);
        v = (v >> 1) | (c << 7);
        zn(v);
        return v;
    }

    private void rorMem(int addr) {
        write(addr, rorVal(read(addr)));
    }

    private void inc(int addr) {
        int v = (read(addr) + 1) & 0xFF;
        write(addr, v);
        zn(v);
    }

    private void dec(int addr) {
        int v = (read(addr) - 1) & 0xFF;
        write(addr, v);
        zn(v);
    }

    private int imm() {
        return pc++;
    }

    private int zp() {
        return fetch();
    }

    private int zpx() {
        return (fetch() + x) & 0xFF;
    }

    private int zpy() {
        return (fetch() + y) & 0xFF;
    }

    private int abs_() {
        int lo = fetch();
        return lo | (fetch() << 8);
    }

    private int[] abx() {
        int base = abs_();
        int addr = (base + x) & 0xFFFF;
        return new int[] {addr, ((base ^ addr) & 0x100) != 0 ? 1 : 0};
    }

    private int[] aby() {
        int base = abs_();
        int addr = (base + y) & 0xFFFF;
        return new int[] {addr, ((base ^ addr) & 0x100) != 0 ? 1 : 0};
    }

    private int abxWrite() {
        return (abs_() + x) & 0xFFFF;
    }

    private int abyWrite() {
        return (abs_() + y) & 0xFFFF;
    }

    private int izx() {
        int ptr = (fetch() + x) & 0xFF;
        return read(ptr) | (read((ptr + 1) & 0xFF) << 8);
    }

    private int[] izy() {
        int ptr = fetch();
        int base = read(ptr) | (read((ptr + 1) & 0xFF) << 8);
        int addr = (base + y) & 0xFFFF;
        return new int[] {addr, ((base ^ addr) & 0x100) != 0 ? 1 : 0};
    }

    private int izyWrite() {
        int ptr = fetch();
        int base = read(ptr) | (read((ptr + 1) & 0xFF) << 8);
        return (base + y) & 0xFFFF;
    }

    private int fetch() {
        return read(pc++);
    }

    private int read(int addr) {
        return bus.read(addr & 0xFFFF);
    }

    private void write(int addr, int value) {
        bus.write(addr & 0xFFFF, value & 0xFF);
    }

    private int read16(int addr) {
        return read(addr) | (read((addr + 1) & 0xFFFF) << 8);
    }

    private void push(int value) {
        write(0x100 | sp, value);
        sp = (sp - 1) & 0xFF;
    }

    private int pop() {
        sp = (sp + 1) & 0xFF;
        return read(0x100 | sp);
    }

    private void zn(int v) {
        p = (p & ~(Z | N)) | (v == 0 ? Z : 0) | (v & N);
    }

    private void setC(boolean on) {
        p = on ? (p | C) : (p & ~C);
    }

    private void setV(boolean on) {
        p = on ? (p | V) : (p & ~V);
    }
}
