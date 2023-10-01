package targets

import loadStubFromResource

class Esp32c3Target : ESP32Target() {
    override fun init() {
        stub = loadStubFromResource("/stubs/stub_flasher_32c3.json")
    }
}