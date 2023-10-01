import com.fazecast.jSerialComm.SerialPort
import java.io.File

class LibraryTest: FlasherSerialInterface {

    private var port: SerialPort? = null

    fun flash() {
        Flasher(this, false)
            .addBin(File("C:\\Users\\louca\\Documents\\SlimeVR\\SlimeVR-Tracker-ESP\\.pio\\build\\esp32\\bootloader.bin").readBytes(), 4096)
            .addBin(File("C:\\Users\\louca\\Documents\\SlimeVR\\SlimeVR-Tracker-ESP\\.pio\\build\\esp32\\partitions.bin").readBytes(), 32768)
            .addBin(File("C:\\Users\\louca\\.platformio\\packages\\framework-arduinoespressif32@3.20007.0\\tools\\partitions\\boot_app0.bin").readBytes(), 57344)
            .addBin(File("C:\\Users\\louca\\Documents\\SlimeVR\\SlimeVR-Tracker-ESP\\.pio\\build\\esp32\\firmware.bin").readBytes(), 65536)
            .flash()
    }

    override fun openSerial() {
        val ports = SerialPort.getCommPorts()
        val firstPort = ports.first() ?: error("unable to find port")
        if (!firstPort.openPort(1000))
            error("unable to open port")
        port = firstPort
    }


    override fun closeSerial() {
        val p = port ?: error("no port to close");
        try {
            p.closePort()
            println("Port closed")
        } catch (e: Exception) {
            error("unable to close port")
        }
    }

    override fun setDTR(value: Boolean) {
        val p = port ?: error("no port to set DTR");
        if (value)
            p.setDTR()
        else
            p.clearDTR()
    }

    override fun setRTS(value: Boolean) {
        val p = port ?: error("no port to set RTS");
        if (value)
            p.setRTS()
        else
            p.clearRTS()
    }

    override fun write(data: ByteArray) {
        val p = port ?: error("no port to write");
        p.writeBytes(data, data.size)
    }

    override fun read(length: Int): ByteArray {
        val p = port ?: error("no port to read");
        val data = ByteArray(length);
        p.readBytes(data, length)
        return data;
    }

    override fun changeBaud(baud: Int) {
        val p = port ?: error("no port to set the baud");
        if (!p.setBaudRate(baud))
            error("Unable to change baudrate")
    }

    override fun setReadTimeout(timeout: Long) {
        val p = port ?: error("no port to set the timeout");
        p.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, timeout.toInt(), 0);
    }

    override fun availableBytes(): Int {
        val p = port ?: error("no port to check available bytes");
        return p.bytesAvailable()
    }

    override fun flushIOBuffers() {
        val p = port ?: error("no port to flush");
        p.flushIOBuffers()
    }
}

fun main() {
    LibraryTest().flash()
}