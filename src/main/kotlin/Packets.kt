import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.experimental.and

abstract class Packet(open var command: Command) {
    abstract fun encode(): ByteArray;

    fun isPacketSupported(target: FlasherTarget): Boolean {
        return (target.supportedPackets() and command.value).toInt() != 0
    }

    open fun checksum(): Int {
        return 0;
    }
}

abstract class DataPacket(override var command: Command) : Packet(command) {
    abstract fun data(): ByteArray;

    override fun checksum(): Int {
        var checksum = 0xEF
        for (byte in data()) {
            checksum = checksum.xor(byte.toUByte().toInt())
        }
        return checksum
    }
}

enum class Direction(val value: Byte) {
    REQUEST(0x00),
    RESPONSE(0x01);

    companion object {
        fun getByValue(value: Byte): Direction? = directionByValue[value]
    }
}
private val directionByValue = Direction.entries.associateBy { it.value }


enum class Command(val value: Byte) {
    FLASH_BEGIN(0x02),
    FLASH_DATA(0x03),
    FLASH_END(0x04),
    MEM_BEGIN(0x05),
    MEM_END(0x06),
    MEM_DATA(0x07),

    // --- ESP32 or STUB ---
    CHANGE_BAUDRATE(0x0f),
    FLASH_MD5(0x13),

    //----

    READ_REG(0x0a),

    SYNC(0x08);

    companion object {
        fun getByValue(value: Byte): Command? = commandByValue[value]
    }
}
private val commandByValue = Command.entries.associateBy { it.value }


data class CommandPacket(
    var direction: Direction,
    var command: Command,
    var size: Short,
    var checksum: Int,
    var data: ByteArray
) {
    fun encode(): ByteArray {
        return ByteBuffer.allocate(8 + data.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(direction.value)
            put(command.value)
            putShort(size)
            putInt(checksum)
            put(data);
        }.array();
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun toString(): String {
        return "CommandPacket(direction=$direction, command=$command (${command.value.toHexString(HexFormat.Default)}), size=$size, checksum=$checksum)"
    }

    companion object {
        fun createCommand(command: Packet): ByteArray {
            val payload = command.encode();

            if (payload.size > Short.MAX_VALUE)
                error("Payload size too big")

            val packet = CommandPacket(
                Direction.REQUEST,
                command.command,
                payload.size.toShort(),
                command.checksum(),
                payload
            );
            val commandPayload = packet.encode()

            if (commandPayload.size - payload.size != 8)
                error("invalid payload size")
            return commandPayload
        }
    }
}

data class ResponsePacket(
    val direction: Direction,
    val command: Command,
    val value: Int,
    val optionalData: ByteArray?
) {
//    var direction: Direction = Direction.RESPONSE;
//    var command: Command? = null;
//    var value: Int = 0;
//    var optionalData: ByteArray? = null;

//    fun decodeHeader(data: ByteBuffer) {
//        val size = data.array().size;
//
////        direction = Direction.getByValue(data.get()) ?: error("unable to decode direction")
//        if (direction != Direction.RESPONSE) {
//            error("Received response packet with direction not set to response")
//        }
//        command = Command.getByValue(data.get()) ?: error("unable to decode command")
//        data.getShort(); // SKIP SIZE as it is unreliable at best. we can compute size from the packet size itself
//        value = data.getInt();
//
//        if (size  > STATUS_BYTES_LENGTH) {
//            optionalData = data.array().slice(8..< size - STATUS_BYTES_LENGTH).toByteArray()
//        }
//
//    }

    fun optionalData(): ByteArray? {
        return optionalData;
    }

    override fun toString(): String {
        return "ResponsePacket(direction=$direction, command=$command, value=$value)"
    }

    companion object {
        private const val STATUS_BYTES_LENGTH = 2;

        fun decodeResponse(data: ByteArray): ResponsePacket {
            val buff = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

            if (data.size < 8)
                error("Failed to read response")

            val direction = Direction.getByValue(buff.get()) ?: error("unable to decode direction")
            if (direction != Direction.RESPONSE) {
                error("Received response packet with direction not set to response")
            }
            val command = Command.getByValue(buff.get()) ?: error("unable to decode command")
            buff.getShort();

            val value = buff.getInt();

            val optionalData = if (data.size - 8 > STATUS_BYTES_LENGTH) {
                data.slice(8..< data.size - STATUS_BYTES_LENGTH).toByteArray()
            } else {
                null
            }

            val statusBytes = data.slice(data.size - STATUS_BYTES_LENGTH..< data.size)
            if (statusBytes[0] == 1.toByte())
                error("Flashing Error, ${FlashError.getByValue(statusBytes[1])?.meaning ?: "unknown"}")
            return ResponsePacket(
                direction,
                command,
                value,
                optionalData
            );
        }

    }
}

data class FlashBegin(
    val size: Int,
    val blocks: Int,
    val blockSize: Int,
    val offset: Int
): Packet(Command.FLASH_BEGIN) {
    override fun encode(): ByteArray {
        return ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(size)
            putInt(blocks)
            putInt(blockSize)
            putInt(offset)
        }.array()
    }

    override fun toString(): String {
        return "FlashBegin(size=$size, blocks=$blocks, blockSize=$blockSize, offset=$offset)"
    }
}

data class FlashData (
    val size: Int,
    val sequence: Int,
    val data: ByteArray,
) : DataPacket(Command.FLASH_DATA) {

    override fun data(): ByteArray {
        return data
    }

    override fun encode(): ByteArray {
        return ByteBuffer.allocate(16 + data.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(size)
            putInt(sequence)
            putInt(0) // ignored
            putInt(0) // ignored
            put(data)
        }.array()
    }

    override fun toString(): String {
        return "FlashData(size=$size, sequence=$sequence)"
    }
}


data class FlashEnd (
    val action: Int = 0,
): Packet(Command.FLASH_END) {
    override fun encode(): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(action)
        }.array()
    }

    override fun toString(): String {
        return "FlashEnd(action=$action)"
    }
}

data class MemBegin(
    val size: Int,
    val blocks: Int,
    val blockSize: Int,
    val offset: Int
) : Packet(Command.MEM_BEGIN) {
    override fun encode(): ByteArray {
        return ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(size)
            putInt(blocks)
            putInt(blockSize)
            putInt(offset)
        }.array()
    }

    override fun toString(): String {
        return "MemBegin(size=$size, blocks=$blocks, blockSize=$blockSize, offset=$offset)"
    }
}

data class MemEnd(
    val executeFlag: Int,
    val address: Int,
) : Packet(Command.MEM_END) {
    override fun encode(): ByteArray {
        return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(executeFlag)
            putInt(address)
        }.array()
    }

    override fun toString(): String {
        return "MemEnd(executeFlag=$executeFlag, address=$address)"
    }
}

data class MemData(
    val size: Int,
    val sequence: Int,
    val data: ByteArray,
) : DataPacket(Command.MEM_DATA) {
    override fun data(): ByteArray {
        return data
    }

    override fun encode(): ByteArray {
        return ByteBuffer.allocate(16 + data.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(size)
            putInt(sequence)
            putInt(0) // ignored
            putInt(0) // ignored
            put(data)
        }.array()
    }

    override fun toString(): String {
        return "MemData(size=$size, sequence=$sequence, data=${data.contentToString()})"
    }
}

data class ChangeBaudrate(
    val baud: Int,
    val oldBaud: Int,
) : Packet(Command.CHANGE_BAUDRATE) {
    override fun encode(): ByteArray {
        return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(baud)
            putInt(oldBaud)
        }.array()
    }

    override fun toString(): String {
        return "ChangeBaudrate(baud=$baud, mode=$oldBaud)"
    }
}

data class ReadReg(val address: Int) : Packet(Command.READ_REG) {
    override fun encode(): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(address)
        }.array()
    }

    override fun toString(): String {
        return "ReadReg(address=$address)"
    }
}

class Sync: Packet(Command.SYNC) {
    override fun encode(): ByteArray {
        return ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(byteArrayOf(0x07, 0x07, 0x12, 0x20))
            for (i in 0..31) {
                put(0x55)
            }
        }.array()
    }
}

data class FlashMD5(
    val address: Int,
    val size: Int,
): Packet(Command.FLASH_MD5) {
    override fun encode(): ByteArray {
        return ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(address)
            putInt(size)
            putInt(0) // ignored
            putInt(0) // ignored
        }.array()
    }
}

