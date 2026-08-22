package nes.bus;

import nes.apu.Apu;
import nes.cart.Cartridge;
import nes.ppu.Ppu;

/** CPU 地址译码。mapper 特例不进这里。 */
public final class Bus implements CpuMemory {
    private final byte[] ram = new byte[0x800];
    private final Cartridge cart;
    private final Ppu ppu;
    private final Apu apu;
    private int controller;
    private int controllerShift;
    private int dmaStall;

    public Bus(Cartridge cart, Ppu ppu, Apu apu) {
        this.cart = cart;
        this.ppu = ppu;
        this.apu = apu;
    }

    public void setController(int buttons) {
        controller = buttons & 0xFF;
    }

    public int takeDmaStall() {
        int n = dmaStall;
        dmaStall = 0;
        return n;
    }

    @Override
    public int read(int address) {
        address &= 0xFFFF;
        if (address < 0x2000) {
            return ram[address & 0x7FF] & 0xFF;
        }
        if (address < 0x4000) {
            return ppu.readRegister(address);
        }
        if (address == 0x4015) {
            return apu.read4015();
        }
        if (address == 0x4016) {
            int bit = controllerShift & 1;
            controllerShift >>= 1;
            return bit | 0x40;
        }
        if (address == 0x4017) {
            return 0x40;
        }
        if (address >= 0x6000) {
            return cart.cpuRead(address);
        }
        return 0;
    }

    @Override
    public void write(int address, int value) {
        address &= 0xFFFF;
        value &= 0xFF;
        if (address < 0x2000) {
            ram[address & 0x7FF] = (byte) value;
            return;
        }
        if (address < 0x4000) {
            ppu.writeRegister(address, value);
            return;
        }
        if (address == 0x4014) {
            int page = value << 8;
            for (int i = 0; i < 256; i++) {
                ppu.writeOam(read(page + i));
            }
            dmaStall += 513;
            return;
        }
        if (address == 0x4016) {
            if ((value & 1) != 0) {
                controllerShift = controller;
            }
            return;
        }
        if (address <= 0x4017) {
            apu.write(address, value);
            return;
        }
        if (address >= 0x6000) {
            cart.cpuWrite(address, value);
        }
    }
}
