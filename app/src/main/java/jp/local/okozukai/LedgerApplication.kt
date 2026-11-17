package jp.local.okozukai

import android.app.Application
import jp.local.okozukai.data.local.LedgerDatabase
import jp.local.okozukai.data.repository.LedgerRepository

class AppContainer(application: Application, name: String = "okozukai.db") {
    private val database by lazy { LedgerDatabase.create(application,name) }
    val repository by lazy { LedgerRepository(database) }
}
open class LedgerApplication : Application() {
    open val container by lazy { AppContainer(this) }
}
