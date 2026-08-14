package ru.maxalert.notifier.max

import com.github.luben.zstd.ZstdInputStream
import java.io.ByteArrayInputStream

/**
 * Zstd is the other codec MAX may pick for a big response (flags = 0xFF), typically the
 * chat list right after login. Streaming, because the frame does not always carry its
 * uncompressed size.
 */
object Zstd {

    private const val MAX_OUTPUT = 5 * 1024 * 1024

    fun decompress(source: ByteArray): ByteArray =
        ZstdInputStream(ByteArrayInputStream(source)).use { stream ->
            val output = stream.readBytes()
            check(output.size <= MAX_OUTPUT) { "Zstd: output too large" }
            output
        }
}
