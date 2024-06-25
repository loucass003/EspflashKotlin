import com.fazecast.jSerialComm.SerialPort
import dev.llelievr.espflashkotlin.Flasher
import dev.llelievr.espflashkotlin.FlasherSerialInterface
import dev.llelievr.espflashkotlin.FlashingProgressListener
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URL

class LibraryTest: FlasherSerialInterface, FlashingProgressListener {

    private var port: SerialPort? = null

    @Test()
    fun flash() {
        val ports = SerialPort.getCommPorts()
        val firstPort = ports.first() ?: error("unable to find port")

        Flasher(this, true)
            .addProgressListener(this)
            // ESP32
//            .addBin(File("C:\\Users\\llelievr\\Downloads\\firmware\\firmware-part-0.bin").readBytes(), 4096)
//            .addBin(File("C:\\Users\\llelievr\\Downloads\\firmware\\firmware-part-1.bin").readBytes(), 32768)
//            .addBin(File("C:\\Users\\llelievr\\Downloads\\firmware\\firmware-part-2.bin").readBytes(), 57344)
//            .addBin(File("C:\\Users\\llelievr\\Downloads\\firmware\\firmware-part-3.bin").readBytes(), 65536)
            .addBin(File("C:\\Users\\llelievr\\Downloads\\firmware (1)\\firmware-part-0.bin").readBytes(), 0)
            .flash(firstPort)
    }

    fun downloadFirmware(url: String): ByteArray? {
        val outputStream = ByteArrayOutputStream()

        try {
            val chunk = ByteArray(4096)
            var bytesRead: Int
            val stream: InputStream = URL(url).openStream()
            while (stream.read(chunk).also { bytesRead = it } > 0) {
                outputStream.write(chunk, 0, bytesRead)
            }
        } catch (e: IOException) {
            error("Cant download firmware $url")
        }

        return outputStream.toByteArray()
    }

    override fun openSerial(port: Any) {
        if (port !is SerialPort)
            error("Not a serial port")
        if (!port.openPort(1000))
            error("unable to open port")
        this.port = port
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

    override fun progress(progress: Float) {
        println("Progress ${progress * 100}")
    }
}

fun main() {
    LibraryTest().flash()
}