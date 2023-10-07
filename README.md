[![](https://jitpack.io/v/loucass003/EspflashKotlin.svg)](https://jitpack.io/#loucass003/EspflashKotlin)


# EspflashKotlin

This library is a simplified implementation of esptool, it allows to flash esp board
and allows to use any serial library.

Here is a list of the known serial libraries that are supported:
- JSerialComm
- And probably more, who knows....

## Example use case
```kotlin
import dev.llelievr.espflashkotlin.Flasher
import dev.llelievr.espflashkotlin.FlasherSerialInterface
import dev.llelievr.espflashkotlin.FlashingProgressListener
import com.fazecast.jSerialComm.SerialPort
import java.io.File

class MyFlasher: FlasherSerialInterface, FlashingProgressListener {

    private var port: SerialPort? = null

    fun flash() {
        val ports = SerialPort.getCommPorts()
        val firstPort = ports.first() ?: error("unable to find port")
        
        // Declare a flasher and its interface
        Flasher(this)
            // Watch for the upload progress 
            .addProgressListener(this)
            // Add one or more binaries to flash
            .addBin(File("bootloader.bin").readBytes(), 4096)
            .addBin(File("partitions.bin").readBytes(), 32768)
            .addBin(File("boot_app0.bin").readBytes(), 57344)
            .addBin(File("firmware.bin").readBytes(), 65536)
            // start the flashing
            .flash(firstPort)
    }

    override fun openSerial(port: Any) {
        if (port !is SerialPort)
            error("Not a serial port")
        if (!port.openPort(1000))
            error("unable to open port")
        this.port = port
    }

    override fun closeSerial() {
        val p = port ?: error("no port to close")
        try {
            p.closePort()
            println("Port closed")
        } catch (e: Exception) {
            error("unable to close port")
        }
    }

    override fun setDTR(value: Boolean) {
        val p = port ?: error("no port to set DTR")
        if (value)
            p.setDTR()
        else
            p.clearDTR()
    }

    override fun setRTS(value: Boolean) {
        val p = port ?: error("no port to set RTS")
        if (value)
            p.setRTS()
        else
            p.clearRTS()
    }

    override fun write(data: ByteArray) {
        val p = port ?: error("no port to write")
        p.writeBytes(data, data.size)
    }

    override fun read(length: Int): ByteArray {
        val p = port ?: error("no port to read")
        val data = ByteArray(length)
        p.readBytes(data, length)
        return data
    }

    override fun changeBaud(baud: Int) {
        val p = port ?: error("no port to set the baud")
        if (!p.setBaudRate(baud))
            error("Unable to change baudrate")
    }

    override fun setReadTimeout(timeout: Long) {
        val p = port ?: error("no port to set the timeout")
        p.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, timeout.toInt(), 0)
    }

    override fun availableBytes(): Int {
        val p = port ?: error("no port to check available bytes")
        return p.bytesAvailable()
    }

    override fun flushIOBuffers() {
        val p = port ?: error("no port to flush")
        p.flushIOBuffers()
    }

    override fun progress(progress: Float) {
        println("Progress ${progress * 100}")
    }
}
```