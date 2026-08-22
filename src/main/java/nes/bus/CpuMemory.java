package nes.bus;

/** CPU 只通过这个口访存。 */
public interface CpuMemory {
    int read(int address);

    void write(int address, int value);
}
