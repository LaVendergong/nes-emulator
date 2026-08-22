package nes.console;

import nes.apu.Apu;
import nes.bus.Bus;
import nes.cart.Cartridge;
import nes.cart.InesRom;
import nes.cpu.Cpu;
import nes.ppu.Ppu;

/** Host 的唯一入口。 */
public final class Console {
    private final Ppu ppu;
    private final Apu apu;
    private final Bus bus;
    private final Cpu cpu;

    public Console(byte[] ines) {
        Cartridge cart = InesRom.load(ines);
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
