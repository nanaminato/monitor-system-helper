package local.helperblock

import android.content.ContentResolver
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore.Downloads
import android.util.Log
import java.io.IOException

internal class PcapStore(private val resolver: ContentResolver) {
    fun write(capture: (Int) -> Unit): String {
        val directory = "${Environment.DIRECTORY_DOWNLOADS}/SystemHelperBlocker"
        val name = "SystemHelperBlocker-${System.currentTimeMillis()}.pcap"
        val values = ContentValues().apply {
            put(Downloads.DISPLAY_NAME, name)
            put(Downloads.MIME_TYPE, "application/vnd.tcpdump.pcap")
            put(Downloads.RELATIVE_PATH, directory)
            put(Downloads.IS_PENDING, 1)
        }
        val file = resolver.insert(Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("无法创建 PCAP 文件。")
        var saved = false
        try {
            val output = resolver.openFileDescriptor(file, "w")
                ?: throw IOException("无法打开 PCAP 文件。")
            output.use { capture(it.fd) }

            values.clear()
            values.put(Downloads.IS_PENDING, 0)
            saved = resolver.update(file, values, null, null) == 1
            if (!saved) throw IOException("无法发布 PCAP 文件。")
            return "$directory/$name"
        } finally {
            if (!saved) {
                try {
                    resolver.delete(file, null, null)
                } catch (error: RuntimeException) {
                    Log.w("PcapStore", "Could not remove pending capture", error)
                }
            }
        }
    }
}
