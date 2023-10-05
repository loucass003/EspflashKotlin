package dev.llelievr.espflashkotlin

enum class FlashError(val value: Byte, val meaning: String) {
    INVALID_MESSAGE(0x05, "Received message is invalid (parameters or length field is invalid)"),
    FAILED_TO_ACT(0x06, "Failed to act on received message"),
    INVALID_CRC(0x07, "Invalid CRC in message"),
    FLASH_WRITE_ERROR(0x08, "Flash write error - after writing a block of data to flash, the ROM loader reads the value back and the 8-bit CRC is compared to the data read from flash. If they don’t match, this error is returned."),
    FLASH_READ_ERROR(0x09, "Flash read error - SPI read failed"),
    FLASH_READ_LENGTH_ERROR(0x0a, "Flash read length error - SPI read request length is too long"),
    DEFLATE_ERROR(0x0b, "Deflate error (compressed uploads only)");

    companion object {
        fun getByValue(value: Byte): FlashError? = errorByValue[value]
    }
}
private val errorByValue = FlashError.entries.associateBy { it.value }