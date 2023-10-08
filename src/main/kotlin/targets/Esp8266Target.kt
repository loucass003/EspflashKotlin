package dev.llelievr.espflashkotlin.targets

import dev.llelievr.espflashkotlin.Command
import dev.llelievr.espflashkotlin.FlasherStub
import dev.llelievr.espflashkotlin.FlasherTarget
import dev.llelievr.espflashkotlin.StubLoader
import kotlin.experimental.or
import kotlin.math.min

class Esp8266Target : FlasherTarget {

    private var stub: FlasherStub? = null;

    override fun getEraseSize(offset: Int, size: Int): Int {
        if (stub !== null) {
            // stub doesn't have same size bug as ROM loader
            return size;
        }

        //Calculate an erase size given a specific size in bytes.
        //Provides a workaround for the bootloader erase bug.
        val sectorCount = (size + FLASH_SECTOR_SIZE - 1) / FLASH_SECTOR_SIZE
        val startSector = offset / FLASH_SECTOR_SIZE

        val headSectors = min(
            FLASH_SECTORS_PER_BLOCK - (startSector % FLASH_SECTORS_PER_BLOCK),
            sectorCount
        )

        return if (sectorCount < 2 * headSectors) {
            (sectorCount + 1) / 2 * FLASH_SECTOR_SIZE
        } else {
            (sectorCount - headSectors) * FLASH_SECTOR_SIZE
        }
    }

    override fun getFlashWriteSize(): Int {
        return if (stub != null) {
            FLASH_WRITE_SIZE_STUB
        } else {
            FLASH_WRITE_SIZE
        }
    }

    override fun init() {
        stub = StubLoader.loadStubFromResource("/stubs/stub_flasher_8266.json")
    }

    override fun stub(): FlasherStub? {
        return stub;
    }

    override fun supportedPackets(): Byte {
        var packets = (
                Command.FLASH_BEGIN.value
                        or Command.FLASH_DATA.value
                        or Command.FLASH_END.value
                        or Command.MEM_BEGIN.value
                        or Command.MEM_END.value
                        or Command.MEM_DATA.value
                        or Command.READ_REG.value
                        or Command.SYNC.value
                )

        if (stub != null) {
            packets = packets or Command.CHANGE_BAUDRATE.value or Command.FLASH_MD5.value
        }

        return packets
    }

    override fun getUploadBaudrate(): Int {
        return UPLOAD_SPEED
    }

    companion object {
        private const val FLASH_SECTOR_SIZE = 0x1000;
        private const val FLASH_WRITE_SIZE = 0x400;
        private const val FLASH_WRITE_SIZE_STUB = 0x4000;
        private const val FLASH_BLOCK_SIZE = 0x100;
        private const val FLASH_SECTORS_PER_BLOCK = FLASH_SECTOR_SIZE / FLASH_BLOCK_SIZE;
        private const val UPLOAD_SPEED = 921600;
    }
}