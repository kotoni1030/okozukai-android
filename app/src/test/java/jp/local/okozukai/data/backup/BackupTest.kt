package jp.local.okozukai.data.backup

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class BackupTest {
    private fun sample() = requireNotNull(javaClass.classLoader?.getResourceAsStream("backup-v1.json")).use { it.readBytes() }
    @Test fun exampleRoundTripPreservesData() {
        val (_,snapshot)=BackupCodec.decode(sample())
        assertEquals(6,snapshot.entries.size)
        val first=java.time.LocalDate.parse("2026-11-01").toEpochDay()
        val next=java.time.LocalDate.parse("2026-12-01").toEpochDay()
        assertEquals(1890L,snapshot.entries.filter { it.kind=="EXPENSE" && it.dateEpochDay>=first && it.dateEpochDay<next }.sumOf { it.amountYen })
        val (_,roundTrip)=BackupCodec.decode(BackupCodec.encode(snapshot,Instant.parse("2026-11-17T00:00:00Z")))
        assertEquals(snapshot,roundTrip)
    }
    @Test fun rejectsSyntaxAndContractViolations() {
        val text=sample().toString(Charsets.UTF_8)
        val bad=listOf(
            text.replace("\"formatVersion\": 1","\"formatVersion\": 2"),
            text.replace("\"amountYen\": 650","\"amountYen\": 6.5e2"),
            text.replace("\"amountYen\": 650","\"amountYen\": \"650\""),
            text.replace("\"amountYen\": 650","\"amountYen\": 650, \"amountYen\": 700"),
            text.replace("\"formatVersion\": 1","\"unknown\": true, \"formatVersion\": 1"),
            text.replace("\"date\": \"2026-11-01\"","\"date\": \"2026-02-29\""),
            text.replace("\"currency\": \"JPY\"","\"currency\": \"USD\""),
            text.replace("\"note\": \"昼ごはん\"","\"note\": null"),
            text.replace("\"kind\": \"INCOME\"","\"kind\": \"EXPENSE\"")
        )
        bad.forEach { assertThrows(Exception::class.java) { BackupCodec.decode(it.toByteArray()) } }
    }
    @Test fun rejectsDeepLargeAndDuplicateStructures() {
        assertThrows(IllegalArgumentException::class.java) { StrictJson.check("[".repeat(15)+"0"+"]".repeat(15)) }
        assertThrows(IllegalArgumentException::class.java) { StrictJson.check("{\"x\":1,\"\\u0078\":2}") }
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(ByteArray(MAX_BYTES+1)) }
        assertThrows(Exception::class.java) { BackupCodec.decode(byteArrayOf(0xc3.toByte(),0x28)) }
    }
}
