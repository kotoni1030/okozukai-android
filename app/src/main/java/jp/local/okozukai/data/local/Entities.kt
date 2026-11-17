package jp.local.okozukai.data.local

import androidx.room.*

@Entity(tableName = "categories", indices = [Index(value = ["kind", "name"], unique = true)])
data class Category(@PrimaryKey val id: String, val kind: String, val name: String, val sortOrder: Int, val isActive: Boolean)
@Entity(tableName = "transactions", foreignKeys = [ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("categoryId"), Index(value = ["dateEpochDay", "createdAt", "id"]), Index(value = ["kind", "dateEpochDay", "categoryId"])])
data class Entry(@PrimaryKey val id: String, val kind: String, val amountYen: Long, val categoryId: String, val dateEpochDay: Long, val note: String, val createdAt: Long, val updatedAt: Long)
@Entity(tableName = "monthly_budgets")
data class MonthlyBudget(@PrimaryKey val month: String, val amountYen: Long)
@Entity(tableName = "category_budgets", primaryKeys = ["month", "categoryId"],
    foreignKeys = [ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("categoryId")])
data class CategoryBudget(val month: String, val categoryId: String, val amountYen: Long)
@Entity(tableName = "savings_goals")
data class Goal(@PrimaryKey val id: String, val name: String, val targetAmountYen: Long, val savedAmountYen: Long, val createdAt: Long, val updatedAt: Long)
data class Totals(val income: Long, val expense: Long)
data class DayTotals(val dateEpochDay: Long, val income: Long, val expense: Long)
data class CategoryTotal(val categoryId: String, val total: Long)
