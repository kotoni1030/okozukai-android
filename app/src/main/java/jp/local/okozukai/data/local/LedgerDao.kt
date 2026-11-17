package jp.local.okozukai.data.local

import androidx.room.*

@Dao
interface LedgerDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, id") suspend fun categories(): List<Category>
    @Query("SELECT * FROM transactions WHERE id=:id") suspend fun entry(id: String): Entry?
    @Query("SELECT * FROM transactions ORDER BY dateEpochDay DESC, createdAt DESC, id DESC") suspend fun entries(): List<Entry>
    @Query("SELECT * FROM monthly_budgets") suspend fun monthlyBudgets(): List<MonthlyBudget>
    @Query("SELECT * FROM category_budgets") suspend fun categoryBudgets(): List<CategoryBudget>
    @Query("SELECT * FROM savings_goals ORDER BY createdAt DESC, id DESC") suspend fun goals(): List<Goal>
    @Query("SELECT (SELECT COUNT(*) FROM categories)+(SELECT COUNT(*) FROM transactions)+(SELECT COUNT(*) FROM monthly_budgets)+(SELECT COUNT(*) FROM category_budgets)+(SELECT COUNT(*) FROM savings_goals)") suspend fun count(): Int
    @Query("""SELECT * FROM transactions WHERE dateEpochDay>=:start AND dateEpochDay<:end
        AND (:category IS NULL OR categoryId=:category)
        AND (:cursorDate IS NULL OR dateEpochDay<:cursorDate OR (dateEpochDay=:cursorDate AND createdAt<:cursorTime) OR (dateEpochDay=:cursorDate AND createdAt=:cursorTime AND id<:cursorId))
        ORDER BY dateEpochDay DESC, createdAt DESC, id DESC LIMIT 50""")
    suspend fun page(start: Long, end: Long, category: String?, cursorDate: Long?, cursorTime: Long?, cursorId: String?): List<Entry>
    @Query("SELECT COALESCE(SUM(CASE WHEN kind='INCOME' THEN amountYen ELSE 0 END),0) AS income, COALESCE(SUM(CASE WHEN kind='EXPENSE' THEN amountYen ELSE 0 END),0) AS expense FROM transactions WHERE dateEpochDay>=:start AND dateEpochDay<:end")
    suspend fun totals(start: Long, end: Long): Totals
    @Query("SELECT dateEpochDay, COALESCE(SUM(CASE WHEN kind='INCOME' THEN amountYen ELSE 0 END),0) AS income, COALESCE(SUM(CASE WHEN kind='EXPENSE' THEN amountYen ELSE 0 END),0) AS expense FROM transactions WHERE dateEpochDay>=:start AND dateEpochDay<:end GROUP BY dateEpochDay")
    suspend fun days(start: Long, end: Long): List<DayTotals>
    @Query("SELECT categoryId, SUM(amountYen) AS total FROM transactions WHERE kind='EXPENSE' AND dateEpochDay>=:start AND dateEpochDay<:end GROUP BY categoryId ORDER BY total DESC")
    suspend fun categoryTotals(start: Long, end: Long): List<CategoryTotal>
    @Query("SELECT * FROM monthly_budgets WHERE month=:month") suspend fun monthly(month: String): MonthlyBudget?
    @Query("SELECT * FROM category_budgets WHERE month=:month") suspend fun budgets(month: String): List<CategoryBudget>
    @Insert suspend fun insertCategories(values: List<Category>)
    @Insert suspend fun insertEntries(values: List<Entry>)
    @Insert suspend fun insertMonths(values: List<MonthlyBudget>)
    @Insert suspend fun insertBudgets(values: List<CategoryBudget>)
    @Insert suspend fun insertGoals(values: List<Goal>)
    @Update suspend fun updateEntry(value: Entry)
    @Update suspend fun updateGoal(value: Goal)
    @Query("DELETE FROM transactions WHERE id=:id") suspend fun deleteEntry(id: String)
    @Query("DELETE FROM savings_goals WHERE id=:id") suspend fun deleteGoal(id: String)
    @Query("DELETE FROM monthly_budgets WHERE month=:month") suspend fun deleteMonth(month: String)
    @Query("DELETE FROM category_budgets WHERE month=:month") suspend fun deleteBudgets(month: String)
    @Query("DELETE FROM transactions") suspend fun clearEntries()
    @Query("DELETE FROM category_budgets") suspend fun clearBudgets()
    @Query("DELETE FROM monthly_budgets") suspend fun clearMonths()
    @Query("DELETE FROM savings_goals") suspend fun clearGoals()
    @Query("DELETE FROM categories") suspend fun clearCategories()
}
