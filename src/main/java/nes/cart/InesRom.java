package nes.cart;

/**
 * 信任边界：校验 iNES。当前接受 0–11、13、19、21–26、34、66、69、71、73、75、79、85、87、140、180、206、210。
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
        boolean nes2 = (flag7 & 0x0C) == 0x08;
        if (prgBanks < 1) {
            throw new IllegalArgumentException("PRG 不能为空");
        }
        if (mapper == 0 && prgBanks != 1 && prgBanks != 2) {
            throw new IllegalArgumentException("NROM 只接受 16K 或 32K PRG");
        }
        switch (mapper) {
            case 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 19, 21, 22, 23, 24, 25, 26, 34, 66, 69, 71, 73, 75, 79, 85,
                    87, 140, 180, 206, 210:
                break;
            default:
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
            chr = new byte[mapper == 13 ? 16384 : 8192];
            chrRam = true;
        } else {
            chr = new byte[chrFile];
            System.arraycopy(file, 16 + trainer + prgSize, chr, 0, chrFile);
            chrRam = false;
        }
        boolean vertical = (flag6 & 0x01) != 0;
        boolean fourScreen = (flag6 & 0x08) != 0;
        Cartridge cart = switch (mapper) {
            case 1 -> new Mmc1(prg, chr, chrRam);
            case 2 -> new Unrom(prg, chr, chrRam, vertical);
            case 3 -> new Cnrom(prg, chr, chrRam, vertical);
            case 4 -> new Mmc3(prg, chr, chrRam, vertical);
            case 5 -> new Mmc5(prg, chr, chrRam);
            case 6 -> new Ffe(prg, chr, chrRam, vertical, false);
            case 7 -> new Axrom(prg, chr, chrRam);
            case 8 -> new Ffe(prg, chr, chrRam, vertical, true);
            case 9 -> new Mmc2(prg, chr, chrRam, false);
            case 10 -> new Mmc2(prg, chr, chrRam, true);
            case 11 -> new ColorDreams(prg, chr, chrRam, vertical);
            case 13 -> new Cprom(prg, chr, vertical);
            case 19 -> new Namco163(prg, chr, chrRam);
            case 21, 23, 25 -> new Vrc24(prg, chr, chrRam, mapper, true, false);
            case 22 -> new Vrc24(prg, chr, chrRam, mapper, false, true);
            case 24 -> new Vrc6(prg, chr, chrRam, false);
            case 26 -> new Vrc6(prg, chr, chrRam, true);
            case 34 -> new Bnrom(prg, chr, chrRam, vertical);
            case 69 -> new Fme7(prg, chr, chrRam);
            case 73 -> new Vrc3(prg, chr, chrRam, vertical);
            case 75 -> new Vrc1(prg, chr, chrRam);
            case 85 -> new Vrc7(prg, chr, chrRam);
            case 66 -> new Gxrom(prg, chr, chrRam, vertical);
            case 71 -> new Camerica(prg, chr, chrRam);
            case 79 -> new Nina(prg, chr, chrRam, vertical);
            case 87 -> new Jaleco(prg, chr, chrRam, vertical, false);
            case 140 -> new Jaleco(prg, chr, chrRam, vertical, true);
            case 180 -> new Unrom180(prg, chr, chrRam, vertical);
            case 206 -> new Dxrom(prg, chr, chrRam, vertical);
            case 210 -> new Namco210(prg, chr, chrRam, vertical, nes2 && ((file[8] >> 4) & 0x0F) == 1);
            default -> new Nrom(prg, chr, chrRam, vertical);
        };
        if (fourScreen && mapper != 5) {
            cart = new FourScreenCart(cart);
        }
        return cart;
    }

    public static final int TV_NTSC = 0;
    public static final int TV_PAL = 1;
    public static final int TV_DENDY = 3;

    /** NES 2.0 byte12：0/2=NTSC，1=PAL，3=Dendy。iNES flags9 bit0=PAL。 */
    public static int tvSystem(byte[] file) {
        if (file == null || file.length < 16) {
            return TV_NTSC;
        }
        if ((file[7] & 0x0C) == 0x08) {
            int tv = file[12] & 3;
            if (tv == 1) {
                return TV_PAL;
            }
            if (tv == 3) {
                return TV_DENDY;
            }
            return TV_NTSC;
        }
        return (file[9] & 1) != 0 ? TV_PAL : TV_NTSC;
    }
}
