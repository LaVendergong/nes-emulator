/**
 * APU（方波/三角/噪声/DMC）。按主时钟推进，产出采样。不碰声卡。
 * 短音：包络 start 未进 quarter 也按 15；三角 $400B 立刻装 linear。
 */
package nes.apu;
