package dev.llelievr.espflashkotlin.targets

import dev.llelievr.espflashkotlin.StubLoader


class Esp32c3Target : ESP32Target() {
    override fun init() {
        stub = StubLoader.loadStubFromResource("/stubs/stub_flasher_32c3.json")
    }
}