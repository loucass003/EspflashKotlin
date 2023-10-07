package dev.llelievr.espflashkotlin

import dev.llelievr.espflashkotlin.targets.ESP32Target
import dev.llelievr.espflashkotlin.targets.Esp32c3Target
import dev.llelievr.espflashkotlin.targets.Esp8266Target
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.logging.*
import kotlin.math.floor
import kotlin.math.max


interface FlasherSerialInterface {

    /**
     * Called when the flasher open the serial port
     */
    fun openSerial(port: Any)

    /**
     * Called when the flasher close the serial port
     * after flashing
     */
    fun closeSerial()

    /**
     * Set the DTR pin value
     * Used to put the mcu in flashing mode
     */
    fun setDTR(value: Boolean);

    /**
     * Set the RTS pin value
     * Used to put the mcu in flashing mode
     */
    fun setRTS(value: Boolean);

    /**
     * Called writing to the serial interface
     */
    fun write(data: ByteArray);

    /**
     * Called when reading from the serial interface
     * Blocking
     */
    fun read(length: Int): ByteArray;

    /**
     * Called to change the baud rate during the flashing
     * the flasher will find the optimal baud rate to flash based on the mcu data
     */
    fun changeBaud(baud: Int);

    /**
     * Used to set the read timeout of the serial interface,
     * This value can change during the flashing process
     */
    fun setReadTimeout(timeout: Long);

    /**
     * Used to know how many bytes are in the serial interface read buffer
     * if your serial library cannot provide this value, simply set it to 1
     */
    fun availableBytes(): Int;

    /**
     * Called to clear the read buffer of the serial interface
     * Used when changing baudrate or trying to sync with the board
     * as unwanted data can be sent by the mcu during those phases
     */
    fun flushIOBuffers();
}

interface FlashingProgressListener {
    fun progress(progress: Float);
}

@OptIn(ExperimentalStdlibApi::class)
class Flasher(
    private val serialInterface: FlasherSerialInterface,
    private val enableTrace: Boolean = false
) {
    private val slipParser = SLIPParser(serialInterface, enableTrace);

    private var currentTarget: FlasherTarget? = null
    private var currentTimeout: Long? = null;
    private val targets = HashMap<Int, FlasherTarget>();
    private val binsToFlash = ArrayList<Pair<Int, ByteArray>>();
    private var flashing: Boolean = false;
    private val progressListeners = ArrayList<FlashingProgressListener>();


    init {
        addTarget(0xFFF0C101.toInt(), Esp8266Target())
        addTarget(0x00F01D83, ESP32Target())

        // Magic value for ESP32C3 eco 1+2 and ESP32C3 eco3 respectively
        addTarget(0x6921506F, Esp32c3Target())
        addTarget(0x1B31506F, Esp32c3Target())
    }

    /**
     * Add a flashing target
     *
     * Used to add new kind of boards that could be unsupported by this library
     */
    fun addTarget(magic: Int, target: FlasherTarget): Flasher {
        if (flashing)
            error("Cannot add a target while flashing")

        targets[magic] = target;
        return this;
    }

    /**
     * Add progress listeners
     *
     * Used to watch flashing progress
     */
    fun addProgressListener(listener: FlashingProgressListener): Flasher {
        progressListeners.add(listener);
        return this;
    }

    /**
     * Add a binary to the list of binaries to flash
     */
    fun addBin(bin: ByteArray, offset: Int): Flasher {
        if (flashing)
            error("Cannot add bin while flashing")

        binsToFlash.add(Pair(offset, bin));
        return this
    }

    /**
     * Start the flashing process
     * Specify the serial port to open
     * Blocking
     */
    fun flash(port: Any) {
        if (flashing)
            error("This flasher is already flashing")
        flashing = true;

        // TODO add checks if binaries are overlapping

        serialInterface.openSerial(port);
        try {
            begin();

            if (binsToFlash.isEmpty())
                error("No binary added to the flasher")

            binsToFlash.sortedBy { it.first }.forEach { pair ->
                writeBinToFlash(pair.second, pair.first);
            }

            end();
        } finally {
            serialInterface.closeSerial();
            flashing = false;
        }
    }

    private fun flashBegin(size: Int, offset: Int) {
        val flasherTarget = currentTarget ?: error("target not set")
        val writeSize = flasherTarget.getFlashWriteSize()
        val blockCount = (size + writeSize - 1) / writeSize
        val eraseSize = flasherTarget.getEraseSize(offset, size)

        println("Write bin, erase = $eraseSize  write = $size ")
        writeWait(
            FlashBegin(
                eraseSize,
                blockCount,
                writeSize,
                offset,
            ),
            timeoutPerMb(eraseSize)
        )
    }

    private fun memBegin(size: Int, offset: Int) {
        val writeSize = ESP_RAM_BLOCK;
        val blockCount = (size + writeSize - 1) / writeSize

        println("Write mem, write = $size ")
        writeWait(
            MemBegin(
                size,
                blockCount,
                writeSize,
                offset,
            )
        )
    }


    private fun loadStub(stub: FlasherStub) {
        memBegin(stub.text.size, stub.text_start)
        val textChunks = stub.text.asSequence().chunked(ESP_RAM_BLOCK)
        textChunks.forEachIndexed { index, chunk ->
            println("Progress ${(index.toFloat() / textChunks.count()) * 100}")
            writeWait(
                MemData(
                    chunk.size,
                    index,
                    chunk.toByteArray()
                ),
            )
        }

        memBegin(stub.data.size, stub.data_start)
        val dataChunks = stub.data.asSequence().chunked(ESP_RAM_BLOCK)
        dataChunks.forEachIndexed { index, chunk ->
            println("Progress ${(index.toFloat() / dataChunks.count()) * 100}")
            writeWait(
                MemData(
                    chunk.size,
                    index,
                    chunk.toByteArray()
                ),
            )
        }

        writeWait(
            MemEnd(if (stub.entry == 0) 1 else 0, stub.entry),
        )


        println("Waiting for OHAI response");
        setReadTimeout(DEFAULT_TIMEOUT);
        val reader = slipParser.getSlipReader() ?: error("no SLIP reader")
        val packetData = reader.next()

        if (!packetData.contentEquals(byteArrayOf(0x4F, 0x48, 0x41, 0x49)))
            println("Failed to start stub, unexpected response: ${packetData.toHexString()}")
        println("Stub running...")
    }

    private fun changeBaud(baud: Int): Boolean {
        val flasherTarget = currentTarget ?: error("target not set")

        val command = ChangeBaudrate(baud, if (flasherTarget.stub() != null) ESP_ROM_BAUD else 0);
        if (!command.isPacketSupported(flasherTarget)) {
            println("Cannot change the baudrate, packet not supported on this target")
            return false;
        }

        writeWait(
            command
        )
        serialInterface.changeBaud(baud);
        Thread.sleep(500) // Get rid of crap sent during baud rate change
        slipParser.flushInput();
        return true;
    }

    private fun writeBinToFlash(bin: ByteArray, offset: Int) {
        val flasherTarget = currentTarget ?: error("target not set")
        val writeSize = flasherTarget.getFlashWriteSize()

        val binMd5 = bytesToMd5(bin);

        flashBegin(bin.size, offset)

        val chunks = bin.asSequence().chunked(writeSize)
        chunks.forEachIndexed { index, chunk ->
            progressListeners.forEach { it.progress((index * writeSize).toFloat() / bin.size) }

            var block = chunk.toByteArray();

            // Pad the last block
            if (block.size < writeSize) {
                block += ByteArray(writeSize - block.size) { 0xff.toByte() }
            }

            writeWait(
                FlashData(
                    block.size,
                    index,
                    block
                ),
            )
        }

        if (flasherTarget.stub() != null) {
            // Stub only writes each block to flash after 'ack'ing the reception,
            // so we do a final dummy operation which will not be 'ack'ed
            // until the last block has actually been written out to flash
            writeWait(
                ReadReg(CHIP_DETECT_MAGIC_REG_ADDR)
            )
        }

        val command = FlashMD5(
            offset,
            bin.size,
        )

        if (command.isPacketSupported(flasherTarget)) {
            val res = writeWait(
                command,
                timeoutPerMb(bin.size, MD5_TIMEOUT_MILLIS_PER_MB)
            )
            val md5Data = res.optionalData() ?: error("could not read md5 from response");
            val md5Str = when (md5Data.size) {
                16 -> md5Data.toHexString()
                8 -> TODO("Unimplemented, cant test it")
                else -> error("Invalid md5 format")
            }

            if (md5Str != binMd5)
                error("MD5 of file does not match data in flash!, local = $binMd5, mcu = $md5Str")
        }
    }

    private fun begin() {
        serialInterface.changeBaud(ESP_ROM_BAUD);

        resetToFlash();

        println("Trying to sync")
        for (i in 10 downTo 0) {
            try {
                slipParser.flushInput();
                writeWait(Sync(), 500)
                break
            } catch (e: IllegalStateException) {
                Thread.sleep(500)
                if (i == 0)
                    error("Could not sync with the device")
            }
        }

        val chipMagicResponse = writeWait(
            ReadReg(CHIP_DETECT_MAGIC_REG_ADDR)
        )
        val target = targets[chipMagicResponse.value]
            ?: error("Unsupported chip, with magic 0x${chipMagicResponse.value.toString(16)}");
        currentTarget = target;

        target.init();

        val stub = target.stub();

        if (stub != null) {
            println("Uploading STUB")
            loadStub(stub);

            val newBaud = target.getUploadBaudrate();
            if (changeBaud(newBaud))
                println("Baudrate changed to $newBaud")
        }
    }

    private fun end() {
        val flasherTarget = currentTarget ?: error("target not set")

        if (flasherTarget.stub() != null) {
            flashBegin(0, 0)

            writeWait(
                FlashEnd(),
            )
        }


        resetAfterFlash();
    }

    private fun resetToFlash() {
        serialInterface.setDTR(false)
        serialInterface.setRTS(true)
        Thread.sleep(100)

        serialInterface.setDTR(true)
        serialInterface.setRTS(false)
        Thread.sleep(50)
        serialInterface.setRTS(true)
    }

    private fun resetAfterFlash() {
        Thread.sleep(100)
        serialInterface.setRTS(true)
        Thread.sleep(100)
        serialInterface.setRTS(false)
    }

    private fun setReadTimeout(timeout: Long) {
        if (currentTimeout != timeout) {
            serialInterface.setReadTimeout(timeout);
            currentTimeout = timeout
        }
    }

    private fun <T : Packet>writeCommand(command: T) {
        val flasherTarget = currentTarget;

        if (flasherTarget != null && !command.isPacketSupported(flasherTarget)) {
            error("Usage of unsupported packet for this target")
        }

        val commandBuf = CommandPacket.createCommand(command)
        val encoded = slipParser.encodeSLIP(commandBuf)

        if (enableTrace) {
            println(
                """
                    ---------------------
                    Command $command
                    packetPayload=${encoded.toHexString()}
                    size=${encoded.size}
                    data=${commandBuf.toHexString()}
                    ---------------------
                """.trimIndent()
            )
        }
        serialInterface.write(encoded);
    }

    private fun <T : Packet>writeWait(command: T, timeout: Long = DEFAULT_TIMEOUT): ResponsePacket {
        writeCommand(command)

        setReadTimeout(timeout);

        val reader = slipParser.getSlipReader() ?: error("No SLIP reader");

        // tries to get a response until that response has the
        // same operation as the request or a retries limit has
        // exceeded. This is needed for some esp8266s that
        // reply with more sync responses than expected.
        var res: ResponsePacket? = null;
        for (retry in 100 downTo 0) {

            val packetData = reader.next()
            res = ResponsePacket.decodeResponse(packetData);

            if (res.command == command.command) {
                if (enableTrace) {
                    println(res.toString())
                }
                return res;
            }
        }
        error("the response does not match the request, req = ${command.command} res = ${res?.command}")
    }

    @Throws(NoSuchAlgorithmException::class)
    private fun bytesToMd5(bytes: ByteArray): String {
        val md5 = MessageDigest.getInstance("MD5")
        md5.update(bytes)
        val digest = md5.digest()
        val md5str = StringBuilder()
        for (b in digest) {
            md5str.append(String.format("%02x", b))
        }
        return md5str.toString()
    }

    private fun timeoutPerMb(size: Int, millisPerMb: Int = ERASE_REGION_MILLIS_PER_MB): Long {
        val defaultTimeout = 3000; // ms

        return max(defaultTimeout, floor(millisPerMb * (size / 1e6)).toInt()).toLong()
    }

    companion object {
        const val CHIP_DETECT_MAGIC_REG_ADDR = 0x40001000;
        const val ESP_RAM_BLOCK = 0x1800;
        const val ESP_ROM_BAUD = 115200;
        const val ERASE_REGION_MILLIS_PER_MB = 30 * 1000;
        const val MD5_TIMEOUT_MILLIS_PER_MB = 8 * 1000;
        const val DEFAULT_TIMEOUT = 3000.toLong()
    }
}