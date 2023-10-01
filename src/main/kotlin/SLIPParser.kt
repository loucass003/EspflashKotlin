import java.io.ByteArrayOutputStream

@OptIn(ExperimentalStdlibApi::class)
class SLIPParser(
    val serialInterface: FlasherSerialInterface,
    val enableTrace: Boolean,
) {
    private var slipReader: Iterator<ByteArray>? = null;

    public fun flushInput() {
        serialInterface.flushIOBuffers();
        slipReader = slipReaderGenerator();
    }

    public fun getSlipReader(): Iterator<ByteArray>? {
        return slipReader;
    }

    fun slipReaderGenerator() = iterator<ByteArray> {
        val partialPacket = ByteArrayOutputStream();
        var packetStarted = false;
        var inEscape = false;
        while (true) {
            val available = serialInterface.availableBytes();
            val toRead = if (available == 0) 1 else available;
            val readBytes = serialInterface.read(toRead);

            if (enableTrace) {
                println("Read ${readBytes.size} bytes: ${readBytes.toHexString()}")
            }
            if (!packetStarted && readBytes[0] == 0.toByte()) {
                error("No serial data received.")
            }

            for (b in readBytes) {
                if (!packetStarted) {
                    if (b == 0xC0.toByte()) {
                        packetStarted = true
                        continue;
                    }
                    error("Read invalid data: ${readBytes.toHexString()}")
                }

                if (b == 0xDB.toByte()) {
                    inEscape = true;
                    continue;
                }

                if (inEscape) {
                    inEscape = false
                    when (b) {
                        0xDC.toByte() -> partialPacket.write(0xC0)
                        0xDD.toByte() -> partialPacket.write(0xDB)
                        else -> error("Invalid slip escape, Read invalid data: ${readBytes.toHexString(HexFormat.Default)}")
                    }
                    continue;
                }

                if (b == 0xC0.toByte()) {
                    yield(partialPacket.toByteArray())
                    packetStarted = false
                    inEscape = false;
                    partialPacket.reset()
                    continue;
                }

                partialPacket.write(b.toInt())
            }
        }
    }

    fun encodeSLIP(input: ByteArray): ByteArray {
        val outputStream = ByteArrayOutputStream()
        outputStream.write(0xC0)
        for (byte in input) {
            when (byte) {
                0xC0.toByte() -> {
                    outputStream.write(0xDB)
                    outputStream.write(0xDC)
                }

                0xDB.toByte() -> {
                    outputStream.write(0xDB)
                    outputStream.write(0xDD)
                }

                else -> outputStream.write(byte.toInt())
            }
        }
        outputStream.write(0xC0)
        return outputStream.toByteArray()
    }
}