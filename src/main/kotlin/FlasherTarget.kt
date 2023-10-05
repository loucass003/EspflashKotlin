package dev.llelievr.espflashkotlin

interface FlasherTarget {

    /**
     * Called when the target is picked by the flasher
     *
     * it is recommended to load the stub here
     */
    fun init();

    /**
     * Get the size to erase on the board before flashing based on the offset of the firmware and the size of the bin
     */
    fun getEraseSize(offset: Int, size: Int): Int;

    /**
     * Get the size of a chunk to write on the mcu
     */
    fun getFlashWriteSize(): Int;

    /**
     * Get the stub loader binaries
     * Can be set to null if no stub available
     */
    fun stub(): FlasherStub?;

    /**
     * List the packets supported by this target
     */
    fun supportedPackets(): Byte;

    /**
     * Get the maximum flash speed of the target
     */
    fun getUploadBaudrate(): Int;
}