package dev.llelievr.espflashkotlin

import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Base64

@Serializable
data class FlasherStubData(
    val entry: Int,
    val text: String,
    val text_start: Int,
    val data: String,
    val data_start: Int,
)

data class FlasherStub(val entry: Int, val text: ByteArray, val text_start: Int, val data: ByteArray, val data_start: Int)

private fun fromData(data: FlasherStubData): FlasherStub {
    return FlasherStub(
        data.entry,
        Base64.getDecoder().decode(data.text),
        data.text_start,
        Base64.getDecoder().decode(data.data),
        data.data_start
    )
}

fun loadStubFromResource(resource: String): FlasherStub? {
    val stubJson = {}.javaClass.getResource(resource)?.readText() ?: return null;
    val decodedStub = Json.decodeFromString<FlasherStubData>(stubJson)

    return fromData(decodedStub)
}

fun loadStubFromFile(resource: File): FlasherStub? {
    if (!resource.exists())
        return null;
    val stubJson = resource.readText()
    val decodedStub = Json.decodeFromString<FlasherStubData>(stubJson);

    return fromData(decodedStub);
}