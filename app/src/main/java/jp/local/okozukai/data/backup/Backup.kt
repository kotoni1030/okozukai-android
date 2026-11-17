package jp.local.okozukai.data.backup

import jp.local.okozukai.data.local.*
import jp.local.okozukai.model.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

const val MAX_BYTES = 32 * 1024 * 1024
@Serializable data class Backup(val format: String, val formatVersion: Int, val exportedAt: String, val appVersion: String, val currency: String,
    val categories: List<CategoryDto>, val transactions: List<EntryDto>, val monthlyBudgets: List<MonthDto>, val categoryBudgets: List<BudgetDto>, val savingsGoals: List<GoalDto>)
@Serializable data class CategoryDto(val id: String, val kind: String, val name: String, val sortOrder: Int, val isActive: Boolean)
@Serializable data class EntryDto(val id: String, val kind: String, val amountYen: Long, val categoryId: String, val date: String, val note: String, val createdAt: String, val updatedAt: String)
@Serializable data class MonthDto(val month: String, val amountYen: Long)
@Serializable data class BudgetDto(val month: String, val categoryId: String, val amountYen: Long)
@Serializable data class GoalDto(val id: String, val name: String, val targetAmountYen: Long, val savedAmountYen: Long, val createdAt: String, val updatedAt: String)
data class Snapshot(val categories: List<Category>, val entries: List<Entry>, val months: List<MonthlyBudget>, val budgets: List<CategoryBudget>, val goals: List<Goal>) {
    val count get() = categories.size + entries.size + months.size + budgets.size + goals.size
    fun dto(now: Instant) = Backup("okozukai-backup",1,Instant.ofEpochMilli(now.toEpochMilli()).toString(),"0.1.0","JPY",
        categories.map { CategoryDto(it.id,it.kind,it.name,it.sortOrder,it.isActive) },
        entries.map { EntryDto(it.id,it.kind,it.amountYen,it.categoryId,LocalDate.ofEpochDay(it.dateEpochDay).toString(),it.note,Instant.ofEpochMilli(it.createdAt).toString(),Instant.ofEpochMilli(it.updatedAt).toString()) },
        months.map { MonthDto(it.month,it.amountYen) }, budgets.map { BudgetDto(it.month,it.categoryId,it.amountYen) },
        goals.map { GoalDto(it.id,it.name,it.targetAmountYen,it.savedAmountYen,Instant.ofEpochMilli(it.createdAt).toString(),Instant.ofEpochMilli(it.updatedAt).toString()) })
}
object BackupCodec {
    private val json = Json { ignoreUnknownKeys = false; isLenient = false }
    fun encode(snapshot: Snapshot, now: Instant): ByteArray {
        val dto = snapshot.dto(now)
        validate(dto)
        return json.encodeToString(dto).toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_BYTES) { "ファイル容量の上限を超えています" } }
    }
    fun decode(bytes: ByteArray): Pair<Backup, Snapshot> {
        require(bytes.size <= MAX_BYTES) { "32MiB以下のファイルを選んでください" }
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        val text = decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        StrictJson.check(text)
        val dto = try { json.decodeFromString<Backup>(text) } catch (_: SerializationException) {
            throw IllegalArgumentException("JSONの型・必須項目・未知の項目を確認してください")
        }
        return dto to validate(dto)
    }
    fun validate(b: Backup): Snapshot {
        require(b.format == "okozukai-backup" && b.formatVersion == 1 && b.currency == "JPY") { "対応していないバックアップ形式です" }
        instant(b.exportedAt); require(b.appVersion.length in 1..80)
        require(b.categories.size <= 200 && b.savingsGoals.size <= 200)
        require(b.categories.size.toLong()+b.transactions.size+b.monthlyBudgets.size+b.categoryBudgets.size+b.savingsGoals.size <= MAX_RECORDS) { "件数上限を超えています" }
        unique(b.categories.map { it.id }); unique(b.categories.map { it.kind to it.name })
        unique(b.transactions.map { it.id }); unique(b.monthlyBudgets.map { it.month })
        unique(b.categoryBudgets.map { it.month to it.categoryId }); unique(b.savingsGoals.map { it.id })
        b.categories.forEach { uuid(it.id); Kind.valueOf(it.kind); require(cleanText(it.name,1,30)==it.name); require(it.sortOrder >= 0) }
        Kind.entries.forEach { kind -> require(b.categories.any { it.kind == kind.name && it.isActive }) { "収入・支出の有効なカテゴリが必要です" } }
        val categories = b.categories.associateBy { it.id }
        var total = 0L
        val entries = b.transactions.map {
            uuid(it.id); require(it.amountYen in 1..MAX_YEN); total = Math.addExact(total,it.amountYen)
            require(categories[it.categoryId]?.kind == it.kind); Kind.valueOf(it.kind)
            require(cleanText(it.note,0,200)==it.note)
            val created = instant(it.createdAt); val updated = instant(it.updatedAt); require(created<=updated)
            Entry(it.id,it.kind,it.amountYen,it.categoryId,date(it.date).toEpochDay(),it.note,created,updated)
        }
        val months = b.monthlyBudgets.map { month(it.month); require(it.amountYen in 0..MAX_YEN); MonthlyBudget(it.month,it.amountYen) }
        val budgets = b.categoryBudgets.map { month(it.month); require(it.amountYen in 0..MAX_YEN); require(categories[it.categoryId]?.kind=="EXPENSE"); CategoryBudget(it.month,it.categoryId,it.amountYen) }
        val goals = b.savingsGoals.map {
            uuid(it.id); require(cleanText(it.name,1,40)==it.name); require(it.targetAmountYen in 1..MAX_YEN && it.savedAmountYen in 0..MAX_YEN)
            val created=instant(it.createdAt); val updated=instant(it.updatedAt); require(created<=updated)
            Goal(it.id,it.name,it.targetAmountYen,it.savedAmountYen,created,updated)
        }
        return Snapshot(b.categories.map { Category(it.id,it.kind,it.name,it.sortOrder,it.isActive) },entries,months,budgets,goals)
    }
    private fun instant(value: String): Long {
        require(value.endsWith("Z")) { "時刻はUTCで指定してください" }
        val i = Instant.parse(value)
        require(i.nano % 1_000_000 == 0)
        return i.toEpochMilli()
    }
    private fun uuid(value: String) { require(UUID.fromString(value).toString()==value) { "UUIDの表記が不正です" } }
    private fun <T> unique(values: List<T>) { require(values.toSet().size==values.size) { "IDまたはキーが重複しています" } }
}

// 通常のJSONデコードが上書きしてしまう重複キーを、デコード前に拒否する。
internal object StrictJson {
    fun check(text: String) { Scanner(text).run() }
    private class Scanner(val s: String) {
        var p=0
        var records=0
        fun run() { value(0); ws(); require(p==s.length) { "JSONの末尾が不正です" } }
        fun ws() { while(p<s.length && s[p] in " \r\n\t") p++ }
        fun take(c: Char) { ws(); require(p<s.length && s[p++]==c) { "JSONの構文が不正です" } }
        fun string(): String {
            ws(); val start=p; take('"')
            while(p<s.length) {
                val c=s[p++]
                if(c=='"') return Json.decodeFromString<String>(s.substring(start,p))
                if(c=='\\') { require(p<s.length); p++ }
                else require(c.code>=32)
            }
            error("文字列が閉じられていません")
        }
        fun value(depth: Int) {
            require(depth<=12) { "JSONの入れ子が深すぎます" }; ws(); require(p<s.length)
            when(s[p]) {
                '{' -> {
                    p++; ws(); val keys=hashSetOf<String>()
                    if(p<s.length && s[p]=='}') { p++; return }
                    do { val key=string(); require(keys.add(key)) { "JSONのキーが重複しています" }; take(':'); ws()
                        if(key in setOf("formatVersion","sortOrder","amountYen","targetAmountYen","savedAmountYen")) require(p<s.length && (s[p] in '0'..'9' || s[p]=='-')) { "金額・版番号・並び順は整数で指定してください" }
                        if(key=="isActive") require(s.startsWith("true",p) || s.startsWith("false",p)) { "isActiveは真偽値で指定してください" }
                        value(depth+1); ws()
                        require(p<s.length); if(s[p]=='}') { p++; return }; take(',')
                    } while(true)
                }
                '[' -> {
                    p++; ws(); if(p<s.length && s[p]==']') { p++; return }
                    do { require(++records<=MAX_RECORDS+5) { "件数上限を超えています" }; value(depth+1); ws()
                        require(p<s.length); if(s[p]==']') { p++; return }; take(',')
                    } while(true)
                }
                '"' -> { string() }
                't' -> literal("true")
                'f' -> literal("false")
                'n' -> throw IllegalArgumentException("nullは許可されません")
                else -> {
                    val start=p
                    if(s[p]=='-') p++
                    require(p<s.length && s[p] in '0'..'9')
                    if(s[p]=='0') p++ else while(p<s.length && s[p] in '0'..'9') p++
                    require(p==s.length || s[p] in ",}] \r\n\t") { "数値は指数・小数を含まない整数で指定してください" }
                    require(s.substring(start,p).toLongOrNull()!=null)
                }
            }
        }
        fun literal(v: String) { require(s.startsWith(v,p)); p+=v.length }
    }
}
