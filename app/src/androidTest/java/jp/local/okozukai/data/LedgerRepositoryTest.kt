package jp.local.okozukai.data

import androidx.test.platform.app.InstrumentationRegistry
import jp.local.okozukai.data.local.*
import jp.local.okozukai.data.repository.LedgerRepository
import jp.local.okozukai.data.backup.Snapshot
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.YearMonth
import java.util.UUID

class LedgerRepositoryTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db:LedgerDatabase
    private lateinit var repo:LedgerRepository
    private lateinit var name:String
    @Before fun setup() {name="test-ledger-"+UUID.randomUUID()+".db"; db=LedgerDatabase.create(context,name); repo=LedgerRepository(db)}
    @After fun cleanup() {db.close(); context.deleteDatabase(name)}
    @Test fun saveReopenAndMonthlySqlTotals()=runBlocking {
        val food=initialCategories()[0].id
        repo.saveEntry(null,"EXPENSE","650",food,"2026-11-01","架空の昼食",0)
        repo.saveEntry(null,"EXPENSE","500",food,"2026-10-31","",0)
        repo.saveEntry(null,"INCOME","50000",initialCategories()[7].id,"2026-11-03","",0)
        assertEquals(Totals(50000,650),repo.load(YearMonth.of(2026,11)).totals)
        val entry=repo.page(YearMonth.of(2026,11),food).single()
        repo.saveEntry(entry.id,"EXPENSE","600",food,"2026-11-02","修正",0)
        assertEquals(600L,repo.load(YearMonth.of(2026,11)).totals.expense)
        db.close(); db=LedgerDatabase.create(context,name); repo=LedgerRepository(db)
        assertEquals(10,repo.load(YearMonth.of(2026,11)).categories.size)
        assertEquals(600L,repo.load(YearMonth.of(2026,11)).totals.expense)
        repo.deleteEntry(entry.id,0)
        assertEquals(0L,repo.load(YearMonth.of(2026,11)).totals.expense)
    }
    @Test fun restoreRollbackAndStaleDraftProtection()=runBlocking {
        repo.saveEntry(null,"EXPENSE","650",initialCategories()[0].id,"2026-11-01","架空",0)
        val before=repo.snapshot()
        val empty=Snapshot(initialCategories(),emptyList(),emptyList(),emptyList(),emptyList())
        try {repo.restore(empty,failAfterClear=true); fail("故障注入が必要")} catch(_:IllegalStateException) {}
        assertEquals(before,repo.snapshot())
        repo.restore(empty)
        assertEquals(0,repo.snapshot().entries.size)
        try {repo.saveEntry(null,"EXPENSE","100",initialCategories()[0].id,"2026-11-01","古い入力",0); fail("古いフォームを拒否")} catch(_:IllegalArgumentException) {}
        repo.restore(before); repo.restore(before)
        assertEquals(before,repo.snapshot())
    }
    @Test fun pagingSameDateAndTimeAndDailyTotals()=runBlocking {
        val day=java.time.LocalDate.parse("2026-11-02").toEpochDay()
        val rows=(1..120).map {Entry(UUID.randomUUID().toString(),"EXPENSE",1,initialCategories()[0].id,day,"",1,1)}
        db.ledger().insertEntries(rows)
        val first=repo.page(YearMonth.of(2026,11),null)
        val second=repo.page(YearMonth.of(2026,11),null,first.last())
        val third=repo.page(YearMonth.of(2026,11),null,second.last())
        assertEquals(listOf(50,50,20),listOf(first.size,second.size,third.size))
        assertEquals(120,(first+second+third).map {it.id}.toSet().size)
        assertEquals(120L,repo.load(YearMonth.of(2026,11)).days.single().expense)
    }
    @Test fun budgetsAndGoalsAreIndependentFromTransactions()=runBlocking {
        val food=initialCategories()[0].id
        repo.saveEntry(null,"EXPENSE","650",food,"2026-11-01","",0)
        repo.saveBudgets("2026-11","0",mapOf(food to "1000"),0)
        repo.copyBudget(YearMonth.of(2026,12),0)
        assertEquals(0L,repo.load(YearMonth.of(2026,12)).budget?.amountYen)
        assertEquals(1000L,repo.load(YearMonth.of(2026,12)).budgets.single().amountYen)
        repo.saveGoal(null,"架空の旅行","10000","12000",0)
        assertEquals(650L,repo.load(YearMonth.of(2026,11)).totals.expense)
        assertEquals(12000L,repo.load(YearMonth.of(2026,11)).goals.single().savedAmountYen)
    }
    @Test fun categoryForeignKeyPreventsDeletion()=runBlocking {
        repo.saveEntry(null,"EXPENSE","650",initialCategories()[0].id,"2026-11-01","",0)
        try {db.ledger().clearCategories(); fail("参照中のカテゴリ削除は失敗する必要があります")} catch(_:android.database.sqlite.SQLiteConstraintException) {}
        assertEquals(10,db.ledger().categories().size)
    }
}
