package jp.local.okozukai.data.backup

import java.io.InputStream
import java.io.OutputStream
import java.io.ByteArrayOutputStream

object BackupStreams {
    fun read(stream: InputStream): Pair<Backup,Snapshot> {
        val bytes=stream.use { input ->
            val output=ByteArrayOutputStream()
            val buffer=ByteArray(8192)
            while(true) {
                val n=input.read(buffer)
                if(n<0) break
                require(output.size()+n<=MAX_BYTES) { "32MiB以下のファイルを選んでください" }
                output.write(buffer,0,n)
            }
            output.toByteArray()
        }
        return BackupCodec.decode(bytes)
    }
    fun write(stream: OutputStream, bytes: ByteArray) {
        // flushだけでなくcloseの失敗も呼び出し元へ伝え、成功表示を防ぐ。
        stream.use { it.write(bytes); it.flush() }
    }
}
