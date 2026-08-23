package nes.cart;

/**
 * iNES four-screen：4KB nametable 在 cart，不扩 PPU CIRAM（避免改 NES1）。
 * MMC5 不包（自己管 NT）。
 */
final class FourScreenCart implements Cartridge {
    private final Cartridge inner;
    private final byte[] nt = new byte[0x1000];

    FourScreenCart(Cartridge inner) {
        this.inner = inner;
    }

    @Override
    public int cpuRead(int address) {
        return inner.cpuRead(address);
    }

    @Override
    public void cpuWrite(int address, int value) {
        inner.cpuWrite(address, value);
    }

    @Override
    public int ppuRead(int address) {
        return inner.ppuRead(address);
    }

    @Override
    public void ppuWrite(int address, int value) {
        inner.ppuWrite(address, value);
    }

    @Override
    public int nametableOffset(int address) {
        return address & 0x3FF;
    }

    @Override
    public int nametableRead(int address) {
        return nt[address & 0x0FFF] & 0xFF;
    }

    @Override
    public boolean nametableWrite(int address, int value) {
        nt[address & 0x0FFF] = (byte) value;
        return true;
    }

    @Override
    public void saveState(java.io.DataOutput out) throws java.io.IOException {
        out.writeInt(0x4653);
        out.write(nt);
        inner.saveState(out);
    }

    @Override
    public void loadState(java.io.DataInput in) throws java.io.IOException {
        if (in.readInt() != 0x4653) {
            throw new java.io.IOException("存档不是 four-screen");
        }
        in.readFully(nt);
        inner.loadState(in);
    }

    @Override
    public void clockCpu() {
        inner.clockCpu();
    }

    @Override
    public void onPpuA12(boolean high) {
        inner.onPpuA12(high);
    }

    @Override
    public void onPpuA12Rise() {
        inner.onPpuA12Rise();
    }

    @Override
    public boolean irqAsserted() {
        return inner.irqAsserted();
    }

    @Override
    public void onPpuScanline(int scanline, boolean rendering) {
        inner.onPpuScanline(scanline, rendering);
    }

    @Override
    public void setChrSpriteWindow(boolean sprite) {
        inner.setChrSpriteWindow(sprite);
    }

    @Override
    public void setPpuCoarseX(int coarseX) {
        inner.setPpuCoarseX(coarseX);
    }

    @Override
    public int expansionPulse() {
        return inner.expansionPulse();
    }

    @Override
    public int expansionPcm() {
        return inner.expansionPcm();
    }
}
