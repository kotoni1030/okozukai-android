package jp.local.okozukai

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import java.util.UUID

class LedgerTestRunner: AndroidJUnitRunner() {
    override fun newApplication(cl:ClassLoader?,className:String?,context:Context?):Application =
        super.newApplication(cl,TestLedgerApplication::class.java.name,context)
}
class TestLedgerApplication:LedgerApplication() {
    override val container by lazy { AppContainer(this,"test-ui-"+UUID.randomUUID()+".db") }
}
