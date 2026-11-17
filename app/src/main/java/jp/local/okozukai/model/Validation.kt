package jp.local.okozukai.model

import java.time.LocalDate
import java.time.YearMonth

const val MAX_YEN = 999_999_999L
const val MAX_RECORDS = 20_000
enum class Kind { EXPENSE, INCOME }
fun amount(text: String, allowZero: Boolean = false): Long {
    val normalized = text.trim().map { if (it in '０'..'９') '0' + (it - '０') else it }.joinToString("")
    require(normalized.isNotEmpty() && normalized.all { it in '0'..'9' }) { "円の整数を入力してください" }
    return (normalized.toLongOrNull() ?: throw IllegalArgumentException("金額が大きすぎます")).also {
        require(it in (if (allowZero) 0L else 1L)..MAX_YEN) { "金額の範囲を確認してください" }
    }
}
fun cleanText(text: String, min: Int, max: Int): String = text.trim().also {
    require(it.codePointCount(0, it.length) in min..max) { "${min}〜${max}文字で入力してください" }
}
fun date(text: String): LocalDate {
    require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(text)) { "日付はYYYY-MM-DD形式です" }
    return try { LocalDate.parse(text).also { require(it.year in 1900..2199) } }
    catch (_: Exception) { throw IllegalArgumentException("1900〜2199年の実在する日付を入力してください") }
}
fun month(text: String): YearMonth {
    require(Regex("\\d{4}-\\d{2}").matches(text)) { "月はYYYY-MM形式です" }
    return YearMonth.parse(text).also { require(it.year in 1900..2199) }
}
fun monthRange(value: YearMonth) = value.atDay(1).toEpochDay() to value.plusMonths(1).atDay(1).toEpochDay()
fun remaining(budget: Long?, spent: Long): Long? = budget?.minus(spent)
