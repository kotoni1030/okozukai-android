package jp.local.okozukai.data.backup

import jp.local.okozukai.data.local.*
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.time.Instant
import java.util.UUID

class BackupBoundaryTest {
    @Test fun legalRecordAndUnicodeLimitsRoundTrip() {
        val categories=initialCategories()
        val rows=(1..19990).map { Entry(UUID.randomUUID().toString(),"EXPENSE",999999999,categories[0].id,0,"😀".repeat(200),0,0) }
        val snapshot=Snapshot(categories,rows,emptyList(),emptyList(),emptyList())
        val bytes=BackupCodec.encode(snapshot,Instant.parse("2026-11-17T00:00:00.123456789Z"))
        assertTrue(bytes.size<=MAX_BYTES)
        assertEquals(snapshot,BackupStreams.read(ByteArrayInputStream(bytes)).second)
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.encode(snapshot.copy(entries=rows+rows.first().copy(id=UUID.randomUUID().toString())),Instant.EPOCH)
        }
    }
    @Test fun fileReadAndWriteFailuresPropagate() {
        val badInput=object:InputStream() {override fun read():Int=throw IOException("架空の読込失敗")}
        assertThrows(IOException::class.java) {BackupStreams.read(badInput)}
        val badWrite=object:OutputStream() {override fun write(value:Int) {throw IOException("架空の容量不足")}}
        assertThrows(IOException::class.java) {BackupStreams.write(badWrite,byteArrayOf(1))}
        val badClose=object:ByteArrayOutputStream() {override fun close() {throw IOException("架空のclose失敗")}}
        assertThrows(IOException::class.java) {BackupStreams.write(badClose,byteArrayOf(1))}
        val endless=object:InputStream() {
            override fun read():Int=32
            override fun read(buffer:ByteArray,offset:Int,length:Int):Int {buffer.fill(32,offset,offset+length); return length}
        }
        assertThrows(IllegalArgumentException::class.java) {BackupStreams.read(endless)}
    }
}
