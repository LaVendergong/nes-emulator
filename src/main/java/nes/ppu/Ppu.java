package nes.ppu;

import nes.cart.Cartridge;

/**
 * NTSC PPU：按 dot 推进，产出 256×240。本切片背景 + 精灵，足够 NROM 出第一帧。
 */
public final class Ppu {
    public static final int WIDTH = 256;
    public static final int HEIGHT = 240;

    private final Cartridge cart;
    private final int[] pixels = new int[WIDTH * HEIGHT];
    private final int[] nametable = new int[0x800];
    private final int[] palette = new int[32];
    private final int[] oam = new int[256];

    private int ctrl;
    private int mask;
    private int status;
    private int oamAddr;
    private int v;
    private int t;
    private int fineX;
    private boolean writeLatch;
    private int readBuffer;
    private int scanline;
    private int dot;
    private boolean nmiLine;
    private boolean frameReady;
    private long dots;

    public Ppu(Cartridge cart) {
        this.cart = cart;
    }

    public void reset() {
        ctrl = 0;
        mask = 0;
        status = 0;
        oamAddr = 0;
        v = 0;
        t = 0;
        fineX = 0;
        writeLatch = false;
        readBuffer = 0;
        scanline = 0;
        dot = 0;
        nmiLine = false;
        frameReady = false;
    }

    public void tick() {
        if (scanline < 240 && dot >= 1 && dot <= 256) {
            plot(dot - 1, scanline);
        }
        if (scanline == 241 && dot == 1) {
            status |= 0x80;
            if ((ctrl & 0x80) != 0) {
                nmiLine = true;
            }
            frameReady = true;
        }
        if (scanline == 261 && dot == 1) {
            status &= ~0xE0;
            nmiLine = false;
        }
        if (rendering() && scanline == 261 && dot >= 280 && dot <= 304) {
            v = (v & ~0x7BE0) | (t & 0x7BE0);
        }
        dot++;
        dots++;
        if (dot > 340) {
            dot = 0;
            scanline++;
            if (scanline > 261) {
                scanline = 0;
            }
        }
    }

    public boolean pullNmi() {
        if (!nmiLine) {
            return false;
        }
        nmiLine = false;
        return true;
    }

    public boolean consumeFrame() {
        if (!frameReady) {
            return false;
        }
        frameReady = false;
        return true;
    }

    public int[] framebuffer() {
        return pixels;
    }

    public long dots() {
        return dots;
    }

    public void save(java.io.DataOutput out) throws java.io.IOException {
        writeInts(out, pixels);
        writeInts(out, nametable);
        writeInts(out, palette);
        writeInts(out, oam);
        out.writeInt(ctrl);
        out.writeInt(mask);
        out.writeInt(status);
        out.writeInt(oamAddr);
        out.writeInt(v);
        out.writeInt(t);
        out.writeInt(fineX);
        out.writeBoolean(writeLatch);
        out.writeInt(readBuffer);
        out.writeInt(scanline);
        out.writeInt(dot);
        out.writeBoolean(nmiLine);
        out.writeBoolean(frameReady);
        out.writeLong(dots);
    }

    public void load(java.io.DataInput in) throws java.io.IOException {
        readInts(in, pixels);
        readInts(in, nametable);
        readInts(in, palette);
        readInts(in, oam);
        ctrl = in.readInt();
        mask = in.readInt();
        status = in.readInt();
        oamAddr = in.readInt();
        v = in.readInt();
        t = in.readInt();
        fineX = in.readInt();
        writeLatch = in.readBoolean();
        readBuffer = in.readInt();
        scanline = in.readInt();
        dot = in.readInt();
        nmiLine = in.readBoolean();
        frameReady = in.readBoolean();
        dots = in.readLong();
    }

    private static void writeInts(java.io.DataOutput out, int[] a) throws java.io.IOException {
        out.writeInt(a.length);
        for (int v : a) {
            out.writeInt(v);
        }
    }

    private static void readInts(java.io.DataInput in, int[] a) throws java.io.IOException {
        int n = in.readInt();
        if (n != a.length) {
            throw new java.io.IOException("PPU 数组长度不匹配");
        }
        for (int i = 0; i < n; i++) {
            a[i] = in.readInt();
        }
    }

    public int readRegister(int reg) {
        switch (reg & 7) {
            case 2 -> {
                int result = status;
                status &= ~0x80;
                writeLatch = false;
                return result;
            }
            case 4 -> {
                return oam[oamAddr];
            }
            case 7 -> {
                int addr = v & 0x3FFF;
                int value = ppuRead(addr);
                int out = value;
                if (addr < 0x3F00) {
                    out = readBuffer;
                    readBuffer = value;
                } else {
                    readBuffer = ppuRead(addr - 0x1000);
                }
                v = (v + increment()) & 0x7FFF;
                return out;
            }
            default -> {
                return 0;
            }
        }
    }

    public void writeRegister(int reg, int value) {
        value &= 0xFF;
        switch (reg & 7) {
            case 0 -> {
                boolean was = (ctrl & 0x80) != 0;
                ctrl = value;
                t = (t & ~0x0C00) | ((value & 0x03) << 10);
                if (!was && (ctrl & 0x80) != 0 && (status & 0x80) != 0) {
                    nmiLine = true;
                }
            }
            case 1 -> mask = value;
            case 3 -> oamAddr = value;
            case 4 -> {
                oam[oamAddr] = value;
                oamAddr = (oamAddr + 1) & 0xFF;
            }
            case 5 -> {
                if (!writeLatch) {
                    t = (t & ~0x001F) | (value >> 3);
                    fineX = value & 7;
                    writeLatch = true;
                } else {
                    t = (t & ~0x73E0) | ((value & 0x07) << 12) | ((value & 0xF8) << 2);
                    writeLatch = false;
                }
            }
            case 6 -> {
                if (!writeLatch) {
                    t = (t & ~0x7F00) | ((value & 0x3F) << 8);
                    writeLatch = true;
                } else {
                    t = (t & ~0x00FF) | value;
                    v = t;
                    writeLatch = false;
                }
            }
            case 7 -> {
                ppuWrite(v & 0x3FFF, value);
                v = (v + increment()) & 0x7FFF;
            }
            default -> {
            }
        }
    }

    public void writeOam(int value) {
        oam[oamAddr] = value & 0xFF;
        oamAddr = (oamAddr + 1) & 0xFF;
    }

    private boolean rendering() {
        return (mask & 0x18) != 0;
    }

    private int increment() {
        return (ctrl & 0x04) != 0 ? 32 : 1;
    }

    private void plot(int x, int y) {
        int bg = 0;
        if ((mask & 0x08) != 0 && (x >= 8 || (mask & 0x02) != 0)) {
            bg = backgroundPixel(x, y);
        }
        int sprite = 0;
        boolean spritePri = true;
        boolean spriteZero = false;
        if ((mask & 0x10) != 0 && (x >= 8 || (mask & 0x04) != 0)) {
            int found = spritePixel(x, y);
            sprite = found & 0x0F;
            spritePri = (found & 0x10) != 0;
            spriteZero = (found & 0x20) != 0;
        }
        int bgOpaque = bg & 3;
        int spOpaque = sprite & 3;
        int index;
        if (bgOpaque == 0 && spOpaque == 0) {
            index = 0;
        } else if (bgOpaque == 0) {
            index = 0x10 | sprite;
        } else if (spOpaque == 0) {
            index = bg;
        } else if (spritePri) {
            index = bg;
        } else {
            index = 0x10 | sprite;
        }
        if (spriteZero && bgOpaque != 0 && spOpaque != 0 && x != 255) {
            status |= 0x40;
        }
        int color = palette[mirrorPalette(0x3F00 | index)] & 0x3F;
        if ((mask & 1) != 0) {
            color &= 0x30;
        }
        pixels[y * WIDTH + x] = NES_RGB[color];
    }

    private int backgroundPixel(int x, int y) {
        // ponytail: 用 t+fineX 算像素，不走移位寄存器。天花板：帧中途 $2006 改 v 的卷轴。升级：loopy 按 dot 递增 + 移位器。
        int scrollX = ((t & 0x1F) << 3) | fineX;
        int scrollY = (((t >> 5) & 0x1F) << 3) | ((t >> 12) & 7);
        int nt = (t >> 10) & 3;
        int rx = scrollX + x;
        int ry = scrollY + y;
        int ntx = (nt & 1) ^ ((rx >> 8) & 1);
        int nty = nt & 2;
        if (ry >= 240) {
            ry -= 240;
            nty ^= 2;
        }
        int tileX = (rx >> 3) & 31;
        int tileY = (ry >> 3) & 31;
        int finePx = rx & 7;
        int finePy = ry & 7;
        int base = 0x2000 | (ntx * 0x400) | (nty * 0x400);
        int tile = ppuRead(base + tileY * 32 + tileX);
        int attr = ppuRead(base + 0x3C0 + (tileY / 4) * 8 + (tileX / 4));
        int shift = (tileX & 2) | ((tileY & 2) << 1);
        int pal = (attr >> shift) & 3;
        int pattern = ((ctrl & 0x10) != 0 ? 0x1000 : 0) + tile * 16 + finePy;
        int lo = ppuRead(pattern);
        int hi = ppuRead(pattern + 8);
        int bit = 7 - finePx;
        int pix = ((lo >> bit) & 1) | (((hi >> bit) & 1) << 1);
        if (pix == 0) {
            return 0;
        }
        return (pal << 2) | pix;
    }

    private int spritePixel(int x, int y) {
        int height = (ctrl & 0x20) != 0 ? 16 : 8;
        int result = 0;
        for (int i = 0; i < 64; i++) {
            int sy = oam[i * 4];
            int row = y - (sy + 1);
            if (row < 0 || row >= height) {
                continue;
            }
            int tile = oam[i * 4 + 1];
            int attr = oam[i * 4 + 2];
            int sx = oam[i * 4 + 3];
            int col = x - sx;
            if (col < 0 || col > 7) {
                continue;
            }
            if ((attr & 0x80) != 0) {
                row = height - 1 - row;
            }
            if ((attr & 0x40) != 0) {
                col = 7 - col;
            }
            int pattern;
            if (height == 16) {
                int bank = (tile & 1) << 12;
                int tn = tile & 0xFE;
                if (row >= 8) {
                    tn++;
                    row -= 8;
                }
                pattern = bank + tn * 16 + row;
            } else {
                pattern = ((ctrl & 0x08) != 0 ? 0x1000 : 0) + tile * 16 + row;
            }
            int lo = ppuRead(pattern);
            int hi = ppuRead(pattern + 8);
            int bit = 7 - col;
            int pix = ((lo >> bit) & 1) | (((hi >> bit) & 1) << 1);
            if (pix == 0) {
                continue;
            }
            result = ((attr & 3) << 2) | pix;
            if ((attr & 0x20) != 0) {
                result |= 0x10;
            }
            if (i == 0) {
                result |= 0x20;
            }
            break;
        }
        return result;
    }

    private int ppuRead(int address) {
        address &= 0x3FFF;
        if (address < 0x2000) {
            return cart.ppuRead(address);
        }
        if (address < 0x3F00) {
            return nametable[mirrorNt(address)];
        }
        return palette[mirrorPalette(address)];
    }

    private void ppuWrite(int address, int value) {
        address &= 0x3FFF;
        value &= 0xFF;
        if (address < 0x2000) {
            cart.ppuWrite(address, value);
            return;
        }
        if (address < 0x3F00) {
            nametable[mirrorNt(address)] = value;
            return;
        }
        palette[mirrorPalette(address)] = value;
    }

    private int mirrorNt(int address) {
        return cart.nametableOffset(address);
    }

    private static int mirrorPalette(int address) {
        int i = address & 0x1F;
        if ((i & 0x13) == 0x10) {
            i &= ~0x10;
        }
        return i;
    }

    /** 常用 2C02 近似，给 Host 直接 blit。 */
    private static final int[] NES_RGB = {
        0xFF666666, 0xFF002A88, 0xFF1412A7, 0xFF3B00A4, 0xFF5C007E, 0xFF6E0040, 0xFF6C0600, 0xFF561D00,
        0xFF333500, 0xFF0B4800, 0xFF005200, 0xFF004F08, 0xFF00404D, 0xFF000000, 0xFF000000, 0xFF000000,
        0xFFADADAD, 0xFF155FD9, 0xFF4240FF, 0xFF7527FE, 0xFFA01ACC, 0xFFB71E7B, 0xFFB53120, 0xFF994E00,
        0xFF6B6D00, 0xFF388700, 0xFF0C9300, 0xFF008F32, 0xFF007C8D, 0xFF000000, 0xFF000000, 0xFF000000,
        0xFFFFFEFF, 0xFF64B0FF, 0xFF9290FF, 0xFFC676FF, 0xFFF36AFF, 0xFFFE6ECC, 0xFFFE8170, 0xFFEA9E22,
        0xFFBCBE00, 0xFF88D800, 0xFF5CE430, 0xFF45E082, 0xFF48CDDE, 0xFF4F4F4F, 0xFF000000, 0xFF000000,
        0xFFFFFEFF, 0xFFC0DFFF, 0xFFD3D2FF, 0xFFE8C8FF, 0xFFFBC2FF, 0xFFFEC4EA, 0xFFFECCC5, 0xFFF7D8A5,
        0xFFE4E594, 0xFFCFEF96, 0xFFBDF4AB, 0xFFB3F3CC, 0xFFB5EBF2, 0xFFB8B8B8, 0xFF000000, 0xFF000000
    };
}
