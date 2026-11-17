package jp.local.okozukai.data.local

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Category::class, Entry::class, MonthlyBudget::class, CategoryBudget::class, Goal::class], version = 1, exportSchema = true)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun ledger(): LedgerDao
    companion object {
        fun create(context: Context, name: String = "okozukai.db") =
            Room.databaseBuilder(context, LedgerDatabase::class.java, name)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // 新規DB作成時だけ投入し、復元したカテゴリを起動時に上書きしない。
                        initialCategories().forEach { c ->
                            db.execSQL("INSERT INTO categories(id,kind,name,sortOrder,isActive) VALUES(?,?,?,?,?)",
                                arrayOf<Any>(c.id, c.kind, c.name, c.sortOrder, 1))
                        }
                    }
                }).build()
    }
}
fun initialCategories(): List<Category> {
    val names = listOf("食費", "交通費", "日用品", "趣味・娯楽", "交際費", "学習", "その他", "バイト代", "お小遣い", "その他")
    return names.mapIndexed { i, name -> Category("00000000-0000-4000-8000-" + (i+1).toString().padStart(12,'0'),
        if(i<7) "EXPENSE" else "INCOME", name, if(i<7) i else i-7, true) }
}
