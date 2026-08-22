package nes.console;

import nes.apu.Apu;
import nes.bus.Bus;
import nes.cart.Cartridge;
import nes.cart.InesRom;
import nes.cpu.Cpu;
import nes.ppu.Ppu;

/** Host 的唯一入口。 */
public final class Console {
    private static final int STATE_MAGIC = 0x4E455331;
    private static final int STATE_VERSION = 1;

    private final Cartridge cart;
    private final Ppu ppu;
    private final Apu apu;
    private final Bus bus;
    private final Cpu cpu;

    public Console(byte[] ines) {
        this.cart = InesRom.load(ines);
        this.ppu = new Ppu(cart);
        this.apu = new Apu();
        this.bus = new Bus(cart, ppu, apu);
        this.cpu = new Cpu(bus);
        ppu.reset();
        apu.reset();
        cpu.reset();
        for (int i = 0; i < 7; i++) {
            tickCpuCycle();
        }
    }

    public void setButtons(int pad1) {
        bus.setController(pad1);
    }

    public void stepFrame() {
        int guard = 0;
        while (!ppu.consumeFrame()) {
            int c = cpu.step();
            cpu.stall(bus.takeDmaStall());
            clock(c);
            if (++guard > 200_000) {
                throw new IllegalStateException("一帧超过 20 万条指令，CPU 可能停在错误码");
            }
        }
    }

    public void stepInstruction() {
        int c = cpu.step();
        cpu.stall(bus.takeDmaStall());
        clock(c);
    }

    public int[] framebuffer() {
        return ppu.framebuffer();
    }

    public short[] drainSamples() {
        return apu.drain();
    }

    public int drainSamples(short[] dest) {
        return apu.drainTo(dest);
    }

    public long cpuCycles() {
        return cpu.cycles();
    }

    public long ppuDots() {
        return ppu.dots();
    }

    public int peekCpu(int address) {
        return bus.read(address);
    }

    public byte[] saveState() {
        java.io.ByteArrayOutputStream raw = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream out = new java.io.DataOutputStream(raw);
        try {
            out.writeInt(STATE_MAGIC);
            out.writeInt(STATE_VERSION);
            cpu.save(out);
            bus.save(out);
            ppu.save(out);
            apu.save(out);
            cart.saveState(out);
            out.flush();
            return raw.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("存档失败", e);
        }
    }

    public void loadState(byte[] data) {
        if (data == null || data.length < 8) {
            throw new IllegalArgumentException("存档为空");
        }
        java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(data));
        try {
            if (in.readInt() != STATE_MAGIC) {
                throw new IllegalArgumentException("不是本模拟器存档");
            }
            if (in.readInt() != STATE_VERSION) {
                throw new IllegalArgumentException("存档版本不兼容");
            }
            cpu.load(in);
            bus.load(in);
            ppu.load(in);
            apu.load(in);
            cart.loadState(in);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("存档损坏或与当前盘不匹配", e);
        }
    }

    private void clock(int cpuCycles) {
        for (int i = 0; i < cpuCycles; i++) {
            tickCpuCycle();
        }
    }

    private void tickCpuCycle() {
        ppu.tick();
        ppu.tick();
        ppu.tick();
        apu.tick();
        if (ppu.pullNmi()) {
            cpu.nmi();
        }
        cpu.irq(apu.irqAsserted());
    }
}
