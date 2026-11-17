package jp.local.okozukai.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.*
import jp.local.okozukai.LedgerApplication
import jp.local.okozukai.data.local.*
import jp.local.okozukai.data.repository.*
import jp.local.okozukai.data.backup.*
import jp.local.okozukai.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.YearMonth

data class ScreenState(val loading: Boolean=true, val busy: Boolean=false, val error: String?=null, val message: String?=null,
    val data: MonthData?=null, val rows: List<Entry> = emptyList(), val more: Boolean=false, val reset: Int=0,
    val preview: Pair<Backup,Snapshot>?=null, val messageMonth:String?=null)
class LedgerViewModel(application: Application, private val saved: SavedStateHandle): AndroidViewModel(application) {
    private val repo=(application as LedgerApplication).container.repository
    val month = saved.getStateFlow("month",YearMonth.now().toString())
    val tab = saved.getStateFlow("tab","履歴")
    val filter = saved.getStateFlow<String?>("filter",null)
    private val mutable=MutableStateFlow(ScreenState())
    val state=mutable.asStateFlow()
    val generation get()=repo.generation
    private var reading: Job?=null
    init {
        reload()
        viewModelScope.launch {
            repo.changes.catch { }.collect { if(!state.value.busy) reload() }
        }
    }
    fun tab(value: String) { if(!state.value.busy) saved["tab"]=value }
    fun moveMonth(delta: Long) {
        if(state.value.busy) return
        val next=YearMonth.parse(month.value).plusMonths(delta)
        if(next.year in 1900..2199) { saved["month"]=next.toString(); reload() }
    }
    fun selectMonth(value: String) { if(!state.value.busy) { jp.local.okozukai.model.month(value); saved["month"]=value; reload() } }
    fun filter(value: String?) { if(!state.value.busy) { saved["filter"]=value; saved["tab"]="履歴"; reload() } }
    fun clearMessage() { mutable.update { it.copy(message=null,messageMonth=null) } }
    fun reload() {
        reading?.cancel()
        reading=viewModelScope.launch {
            mutable.update { it.copy(loading=true,error=null,data=null,rows=emptyList(),more=false) }
            try {
                val m=YearMonth.parse(month.value)
                val data=repo.load(m); val rows=repo.page(m,filter.value)
                mutable.update { it.copy(loading=false,data=data,rows=rows,more=rows.size==50) }
            } catch(e: CancellationException) { throw e }
            catch(_: Exception) { mutable.update { it.copy(loading=false,error="保存データを読み込めません。再試行してください。改善しない場合はアプリを削除せず、バックアップの有無を確認してください。") } }
        }
    }
    fun more() {
        if(state.value.busy || state.value.loading || !state.value.more) return
        reading=viewModelScope.launch {
            mutable.update { it.copy(loading=true) }
            try {
                val rows=repo.page(YearMonth.parse(month.value),filter.value,state.value.rows.lastOrNull())
                mutable.update { it.copy(loading=false,rows=it.rows+rows,more=rows.size==50) }
            } catch(e: CancellationException) { throw e }
            catch(_: Exception) { mutable.update { it.copy(loading=false,error="続きを読むことができません。再読込してください。") } }
        }
    }
    private fun action(success: String, work: suspend ()->Unit, done: ()->Unit = {}) {
        if(state.value.busy) return
        reading?.cancel()
        mutable.update { it.copy(busy=true,loading=false,message=null,messageMonth=null,error=null) }
        viewModelScope.launch {
            try {
                work()
                mutable.update { it.copy(busy=false,message=success) }
                done(); reload()
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(busy=false,error=(e as? IllegalArgumentException)?.message ?: "処理に失敗しました。入力を残したまま再試行できます。") } }
        }
    }
    fun saveEntry(id: String?, kind: String, amount: String, category: String, date: String, note: String, expected: Long, done: ()->Unit) =
        action("${date}に保存しました", { repo.saveEntry(id,kind,amount,category,date,note,expected) }, {
            mutable.update { it.copy(messageMonth=YearMonth.from(jp.local.okozukai.model.date(date)).toString()) }; done()
        })
    fun deleteEntry(id: String, expected: Long, done: ()->Unit) = action("削除しました",{repo.deleteEntry(id,expected)},done)
    fun saveBudgets(overall: String, values: Map<String,String>, expected: Long, done: ()->Unit) =
        action("予算を保存しました",{repo.saveBudgets(month.value,overall,values,expected)},done)
    fun copyBudget(expected: Long) = action("前月の予算をコピーしました",{repo.copyBudget(YearMonth.parse(month.value),expected)})
    fun saveGoal(id: String?,name: String,target: String,amount: String,expected: Long,done: ()->Unit) =
        action("目標を保存しました",{repo.saveGoal(id,name,target,amount,expected)},done)
    fun deleteGoal(id: String,expected: Long,done: ()->Unit) = action("目標を削除しました",{repo.deleteGoal(id,expected)},done)
    fun export(uri: Uri) = action("ファイルへの書き込みが完了しました",{
        val snapshot=repo.snapshot()
        withContext(Dispatchers.IO) {
            val bytes=BackupCodec.encode(snapshot,Instant.now())
            val resolver=getApplication<Application>().contentResolver
            try { BackupStreams.write(requireNotNull(resolver.openOutputStream(uri,"wt")),bytes) }
            catch(e: Exception) { throw IllegalArgumentException("書き出しに失敗しました。保存先に未完成のファイルが残っている可能性があります。",e) }
        }
    })
    fun inspect(uri: Uri) = action("バックアップの検証が完了しました", {
        val preview=withContext(Dispatchers.IO) {
            val resolver=getApplication<Application>().contentResolver
            BackupStreams.read(requireNotNull(resolver.openInputStream(uri)))
        }
        mutable.update { it.copy(preview=preview) }
    })
    fun dismissPreview() { if(!state.value.busy) mutable.update { it.copy(preview=null) } }
    fun restore() {
        val preview=state.value.preview ?: return
        action("復元しました", { repo.restore(preview.second) }, {
            saved["month"]=YearMonth.now().toString(); saved["tab"]="履歴"; saved["filter"]=null
            mutable.update { it.copy(preview=null,reset=it.reset+1) }
        })
    }
}
