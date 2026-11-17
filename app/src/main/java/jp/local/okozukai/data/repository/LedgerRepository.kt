package jp.local.okozukai.data.repository

import androidx.room.withTransaction
import jp.local.okozukai.data.local.*
import jp.local.okozukai.data.backup.*
import jp.local.okozukai.model.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

data class MonthData(val categories: List<Category>, val totals: Totals, val days: List<DayTotals>, val categoryTotals: List<CategoryTotal>, val budget: MonthlyBudget?, val budgets: List<CategoryBudget>, val goals: List<Goal>)
class LedgerRepository(private val db: LedgerDatabase, private val clock: Clock = Clock.systemDefaultZone()) {
    private val dao = db.ledger()
    val changes = db.invalidationTracker.createFlow("categories","transactions","monthly_budgets","category_budgets","savings_goals",emitInitialState=false)
    private val writes = Mutex()
    @Volatile var generation = 0L
        private set
    suspend fun load(month: YearMonth): MonthData = db.withTransaction {
        val (start,end) = monthRange(month)
        MonthData(dao.categories(),dao.totals(start,end),dao.days(start,end),dao.categoryTotals(start,end),dao.monthly(month.toString()),dao.budgets(month.toString()),dao.goals())
    }
    suspend fun page(month: YearMonth, category: String?, cursor: Entry? = null): List<Entry> {
        val (start,end)=monthRange(month)
        return dao.page(start,end,category,cursor?.dateEpochDay,cursor?.createdAt,cursor?.id)
    }
    private suspend fun capacity(extra: Int) { require(dao.count()+extra<=MAX_RECORDS) { "保存件数の上限です。不要な記録を整理してください" } }
    private fun current(expected: Long) { require(expected==generation) { "復元後の古い入力です。開き直してください" } }
    suspend fun saveEntry(id: String?, kind: String, amountText: String, categoryId: String, dateText: String, noteText: String, expected: Long) = writes.withLock {
        current(expected)
        val yen=amount(amountText); val day=date(dateText); val note=cleanText(noteText,0,200); Kind.valueOf(kind)
        db.withTransaction {
            val old = id?.let { requireNotNull(dao.entry(it)) { "記録が見つかりません" } }
            val category=dao.categories().firstOrNull { it.id==categoryId }
            require(category!=null && category.kind==kind && (category.isActive || old?.categoryId==categoryId)) { "カテゴリを選択してください" }
            val now=maxOf(clock.instant().toEpochMilli(),old?.updatedAt ?: Long.MIN_VALUE)
            val value=Entry(id ?: UUID.randomUUID().toString(),kind,yen,categoryId,day.toEpochDay(),note,old?.createdAt ?: now,now)
            if(old==null) { capacity(1); dao.insertEntries(listOf(value)) } else dao.updateEntry(value)
        }
    }
    suspend fun deleteEntry(id: String, expected: Long) = writes.withLock { current(expected); db.withTransaction { dao.deleteEntry(id) } }
    suspend fun saveBudgets(month: String, overall: String, values: Map<String,String>, expected: Long) = writes.withLock {
        current(expected); jp.local.okozukai.model.month(month)
        val m=overall.takeIf { it.isNotBlank() }?.let { MonthlyBudget(month,amount(it,true)) }
        val b=values.filterValues { it.isNotBlank() }.map { (id,v) -> CategoryBudget(month,id,amount(v,true)) }
        db.withTransaction {
            val categories=dao.categories().associateBy { it.id }
            require(b.all { categories[it.categoryId]?.kind=="EXPENSE" })
            val old=(if(dao.monthly(month)==null) 0 else 1)+dao.budgets(month).size
            capacity((if(m==null) 0 else 1)+b.size-old)
            dao.deleteBudgets(month); dao.deleteMonth(month)
            if(m!=null) dao.insertMonths(listOf(m)); dao.insertBudgets(b)
        }
    }
    suspend fun copyBudget(month: YearMonth, expected: Long) = writes.withLock {
        current(expected)
        db.withTransaction {
            val previous=month.minusMonths(1).toString()
            val m=dao.monthly(previous); val b=dao.budgets(previous)
            require(m!=null || b.isNotEmpty()) { "前月に予算設定がありません" }
            val key=month.toString()
            val old=(if(dao.monthly(key)==null) 0 else 1)+dao.budgets(key).size
            capacity((if(m==null) 0 else 1)+b.size-old)
            dao.deleteBudgets(key); dao.deleteMonth(key)
            if(m!=null) dao.insertMonths(listOf(m.copy(month=key)))
            dao.insertBudgets(b.map { it.copy(month=key) })
        }
    }
    suspend fun saveGoal(id: String?, name: String, target: String, saved: String, expected: Long) = writes.withLock {
        current(expected); val n=cleanText(name,1,40); val t=amount(target); val s=amount(saved,true)
        db.withTransaction {
            val old=id?.let { key -> requireNotNull(dao.goals().find { it.id==key }) }
            val now=maxOf(clock.instant().toEpochMilli(),old?.updatedAt ?: Long.MIN_VALUE)
            val goal=Goal(id ?: UUID.randomUUID().toString(),n,t,s,old?.createdAt ?: now,now)
            if(old==null) { require(dao.goals().size<200); capacity(1); dao.insertGoals(listOf(goal)) } else dao.updateGoal(goal)
        }
    }
    suspend fun deleteGoal(id: String, expected: Long) = writes.withLock { current(expected); db.withTransaction { dao.deleteGoal(id) } }
    suspend fun snapshot(): Snapshot = db.withTransaction { Snapshot(dao.categories(),dao.entries(),dao.monthlyBudgets(),dao.categoryBudgets(),dao.goals()) }
    suspend fun restore(snapshot: Snapshot, failAfterClear: Boolean = false) = writes.withLock {
        // 同じ検証済みスナップショットを使い、挿入の途中失敗も全体を戻す。
        BackupCodec.validate(snapshot.dto(Instant.now()))
        db.withTransaction {
            dao.clearEntries(); dao.clearBudgets(); dao.clearMonths(); dao.clearGoals(); dao.clearCategories()
            check(!failAfterClear) { "検証用の故障注入" }
            dao.insertCategories(snapshot.categories); dao.insertEntries(snapshot.entries)
            dao.insertMonths(snapshot.months); dao.insertBudgets(snapshot.budgets); dao.insertGoals(snapshot.goals)
        }
        generation++
    }
}
