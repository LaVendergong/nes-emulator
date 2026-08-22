package nes.cart;

/**
 * 信任边界：校验 iNES。当前接受 mapper 0 与 1。
 */
public final class InesRom {
    private InesRom() {}

    public static Cartridge load(byte[] file) {
        if (file == null || file.length < 16) {
            throw new IllegalArgumentException("ROM 太短，不是 iNES");
        }
        if (file[0] != 'N' || file[1] != 'E' || file[2] != 'S' || file[3] != 0x1A) {
            throw new IllegalArgumentException("不是 iNES（缺少 NES\\x1A）");
        }
        int prgBanks = file[4] & 0xFF;
        int chrBanks = file[5] & 0xFF;
        int flag6 = file[6] & 0xFF;
        int flag7 = file[7] & 0xFF;
        int mapper = (flag6 >> 4) | (flag7 & 0xF0);
        if ((flag6 & 0x08) != 0) {
            throw new IllegalArgumentException("不支持 four-screen nametable");
        }
        if (prgBanks < 1) {
            throw new IllegalArgumentException("PRG 不能为空");
        }
        if (mapper == 0 && prgBanks != 1 && prgBanks != 2) {
            throw new IllegalArgumentException("NROM 只接受 16K 或 32K PRG");
        }
        if (mapper != 0 && mapper != 1) {
            throw new IllegalArgumentException("不支持 mapper " + mapper);
        }
        int trainer = (flag6 & 0x04) != 0 ? 512 : 0;
        int prgSize = prgBanks * 16384;
        int chrFile = chrBanks * 8192;
        int need = 16 + trainer + prgSize + chrFile;
        if (file.length < need) {
            throw new IllegalArgumentException("ROM 截断：需要 " + need + " 字节");
        }
        byte[] prg = new byte[prgSize];
        System.arraycopy(file, 16 + trainer, prg, 0, prgSize);
        byte[] chr;
        boolean chrRam;
        if (chrBanks == 0) {
            chr = new byte[8192];
            chrRam = true;
        } else {
            chr = new byte[chrFile];
            System.arraycopy(file, 16 + trainer + prgSize, chr, 0, chrFile);
            chrRam = false;
        }
        if (mapper == 1) {
            return new Mmc1(prg, chr, chrRam);
        }
        boolean vertical = (flag6 & 0x01) != 0;
        return new Nrom(prg, chr, chrRam, vertical);
    }
}
