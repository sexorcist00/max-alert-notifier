package ru.maxalert.notifier.max

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * The anti-bot proof MAX wants before it will send an SMS code.
 *
 * A web client has to solve a captcha instead; the Android client sends this instead, and
 * without it the server answers `captcha.validation-failed` and no SMS is ever sent. It is
 * three SHA-256 hashes over published build hashes of the MAX APK, salted with the seed the
 * server hands out during the handshake and with our device id -- so it is derived, not
 * secret, and it is why the client below introduces itself as the Android app.
 *
 * Values below are build 6790 (app 26.25.0), arm64-v8a. When MAX stops accepting them, the
 * fix is to refresh these three hashes for a newer build.
 */
object MaxFingerprint {

    const val APP_VERSION = "26.25.0"
    const val BUILD_NUMBER = 6790
    const val ARCH = "arm64-v8a"

    private const val CERTIFICATE_META_SHA256 =
        "1684414033eb263e2c615f8b7df5ed8793850a07656304997fbf07e9e21e1e93"
    private const val DEX_META_SHA256 =
        "8db68fcc0e85e8f041fe4a875c0a9bcfe542a8f679603728c651ed81b64dd684"
    private const val SO_META_SHA256 =
        "634ecc42b246784d975f180b4fecf903df235cdf0476da47163a85630eb1a6a8"

    fun generate(callsSeed: Long, deviceId: String): ByteArray {
        val seed = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(callsSeed).array()
        val device = deviceId.toByteArray(Charsets.UTF_8)
        return digest(CERTIFICATE_META_SHA256, seed, device) +
            digest(DEX_META_SHA256, seed, device) +
            digest(SO_META_SHA256, seed, device)
    }

    private fun digest(hashHex: String, seed: ByteArray, device: ByteArray): ByteArray {
        val sha = MessageDigest.getInstance("SHA-256")
        sha.update(hashHex.hexToBytes())
        sha.update(seed)
        sha.update(device)
        return sha.digest()
    }

    private fun String.hexToBytes(): ByteArray =
        ByteArray(length / 2) { index -> substring(index * 2, index * 2 + 2).toInt(16).toByte() }
}
