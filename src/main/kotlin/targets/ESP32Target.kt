package dev.llelievr.espflashkotlin.targets

import dev.llelievr.espflashkotlin.Command
import dev.llelievr.espflashkotlin.FlasherStub
import dev.llelievr.espflashkotlin.FlasherTarget
import dev.llelievr.espflashkotlin.StubLoader
import kotlin.experimental.or



open class ESP32Target : FlasherTarget {
    internal var stub: FlasherStub? = null;

    override fun init() {
        stub = StubLoader.loadStubFromResource("/stubs/stub_flasher_32.json")
    }

    override fun getEraseSize(offset: Int, size: Int): Int {
        return size;
    }

    override fun getFlashWriteSize(): Int {
        return FLASH_WRITE_SIZE;
    }

    override fun stub(): FlasherStub? {
        return stub;
    }

    override fun supportedPackets(): Byte {
        return (
            Command.FLASH_BEGIN.value
                or Command.FLASH_DATA.value
                or Command.FLASH_END.value
                or Command.MEM_BEGIN.value
                or Command.MEM_END.value
                or Command.MEM_DATA.value
                or Command.READ_REG.value
                or Command.SYNC.value
                or Command.CHANGE_BAUDRATE.value
                or Command.FLASH_MD5.value
        )
    }

    override fun getUploadBaudrate(): Int {
        // TODO Check for the esp crystal speed

        return UPLOAD_SPEED
    }

    companion object {
        private const val FLASH_WRITE_SIZE = 0x4000;
        private const val UPLOAD_SPEED = 921600;
    }
}