package jp.local.okozukai.model

import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth

class ValidationTest {
    @Test fun yenUsesOnlyIntegers() {
        assertEquals(650L,amount(" ６５０ "))
        assertEquals(MAX_YEN,amount("999999999"))
        assertEquals(0L,amount("0",true))
        listOf("","0","-1","1.5","1e3","1,000","￥500","1 0","1000000000","999999999999999999999").forEach {
            assertThrows(IllegalArgumentException::class.java) { amount(it) }
        }
    }
    @Test fun datesAndMonthBoundaries() {
        assertEquals("2028-02-29",date("2028-02-29").toString())
        listOf("2026-02-29","1899-12-31","2200-01-01","2026-2-01").forEach { assertThrows(IllegalArgumentException::class.java) { date(it) } }
        val (start,end)=monthRange(YearMonth.of(2026,12))
        assertEquals(date("2026-12-01").toEpochDay(),start)
        assertEquals(date("2027-01-01").toEpochDay(),end)
    }
    @Test fun unicodeAndBudgets() {
        assertEquals("😀".repeat(200),cleanText("😀".repeat(200),0,200))
        assertThrows(IllegalArgumentException::class.java) { cleanText("😀".repeat(201),0,200) }
        assertNull(remaining(null,650)); assertEquals(-650L,remaining(0,650)); assertEquals(0L,remaining(0,0))
    }
}
