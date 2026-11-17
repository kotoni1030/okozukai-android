package jp.local.okozukai.ui

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.local.okozukai.data.local.*
import jp.local.okozukai.model.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.text.NumberFormat
import java.util.Locale

fun yen(value: Long) = NumberFormat.getIntegerInstance(Locale.JAPAN).format(value)+"円"

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LedgerApp(vm: LedgerViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    var editor by rememberSaveable(state.reset) { mutableStateOf<String?>(null) }
    var editingId by rememberSaveable(state.reset) { mutableStateOf<String?>(null) }
    var editingEntry by rememberSaveable(state.reset,stateSaver=entrySaver) { mutableStateOf<Entry?>(null) }
    var editingGoal by rememberSaveable(state.reset,stateSaver=goalSaver) { mutableStateOf<Goal?>(null) }
    val snack=remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snack.showSnackbar(it); vm.clearMessage() } }
    // resetを保存状態のキーにして、復元成功時だけ古いフォームを破棄する。
    BackHandler(enabled=state.busy && editor==null) {}
    val data=state.data
    val close={ editor=null; editingId=null; editingEntry=null; editingGoal=null }
    Scaffold(
        topBar={ TopAppBar(title={Text("お小遣い帳")}, actions={
            TextButton(onClick={vm.tab("設定")},enabled=!state.busy && editor==null) { Text("設定") }
        }) },
        bottomBar={ if(editor==null) NavigationBar {
            listOf("履歴","集計","予算","目標").forEach { name ->
                NavigationBarItem(selected=tab==name,onClick={vm.tab(name)},enabled=!state.busy,
                    icon={Text(if(name=="履歴") "帳" else if(name=="集計") "図" else if(name=="予算") "予" else "目")},label={Text(name)})
            }
        }},
        floatingActionButton={ if(editor==null && tab!="設定" && data!=null) FloatingActionButton(onClick={
            if(!state.busy) { editingEntry=null; editingId=null; editor="取引" }
        }) { Text("＋記録",Modifier.padding(horizontal=12.dp)) } },
        snackbarHost={SnackbarHost(snack)}
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if(state.busy || state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { error ->
                Text(error,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(12.dp))
                if(editor==null) TextButton(onClick=vm::reload,enabled=!state.busy) { Text("再読込") }
            }
            if(data==null) {
                if(!state.loading) Text("データの読み込みを再試行してください",Modifier.padding(16.dp))
            } else if(editor!=null) {
                key(state.reset,editor,editingId) {
                    when(editor) {
                        "取引" -> EntryEditor(editingEntry ?: dataEntry(state.rows,editingId),data.categories,state.busy,vm.generation,
                            save={id,k,a,c,d,n,g -> vm.saveEntry(id,k,a,c,d,n,g,close)},
                            delete={id,g -> vm.deleteEntry(id,g,close)},close=close)
                        "目標" -> GoalEditor(editingGoal ?: data.goals.find { it.id==editingId },state.busy,vm.generation,
                            save={id,n,t,s,g -> vm.saveGoal(id,n,t,s,g,close)},
                            delete={id,g -> vm.deleteGoal(id,g,close)},close=close)
                        "予算" -> BudgetEditor(data,state.busy,vm.generation,save={m,b,g -> vm.saveBudgets(m,b,g,close)},close=close)
                    }
                }
            } else {
                if(tab in listOf("履歴","集計","予算")) MonthHeader(month,!state.busy,vm::moveMonth,vm::selectMonth)
                when(tab) {
                    "履歴" -> History(data,state.rows,state.more,state.busy || state.loading,filter,
                        clearFilter={vm.filter(null)},more=vm::more,edit={ editingEntry=it; editingId=it.id; editor="取引" })
                    "集計" -> Summary(data,vm::filter)
                    "予算" -> BudgetScreen(data,month,state.busy,edit={editor="予算"},copy={vm.copyBudget(vm.generation)})
                    "目標" -> Goals(data.goals,state.busy,add={editingGoal=null; editingId=null; editor="目標"},
                        edit={editingGoal=it; editingId=it.id; editor="目標"})
                    "設定" -> BackupScreen(vm,state)
                }
            }
        }
    }
}
private val entrySaver=Saver<Entry?,List<Any>>(
    save={e -> if(e==null) emptyList() else listOf(e.id,e.kind,e.amountYen,e.categoryId,e.dateEpochDay,e.note,e.createdAt,e.updatedAt)},
    restore={v -> if(v.isEmpty()) null else Entry(v[0] as String,v[1] as String,v[2] as Long,v[3] as String,v[4] as Long,v[5] as String,v[6] as Long,v[7] as Long)})
private val goalSaver=Saver<Goal?,List<Any>>(
    save={g -> if(g==null) emptyList() else listOf(g.id,g.name,g.targetAmountYen,g.savedAmountYen,g.createdAt,g.updatedAt)},
    restore={v -> if(v.isEmpty()) null else Goal(v[0] as String,v[1] as String,v[2] as Long,v[3] as Long,v[4] as Long,v[5] as Long)})
private fun dataEntry(rows: List<Entry>, id: String?)=rows.find { it.id==id }

@Composable private fun MonthHeader(month: String,enabled: Boolean,move: (Long)->Unit,select:(String)->Unit) {
    var choosing by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf(month) }
    var error by remember { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
        TextButton(onClick={move(-1)},enabled=enabled && month>"1900-01") { Text("前月") }
        TextButton(onClick={input=month; choosing=true},enabled=enabled) { Text(month) }
        TextButton(onClick={move(1)},enabled=enabled && month<"2199-12") { Text("翌月") }
    }
    if(choosing) AlertDialog(onDismissRequest={choosing=false},title={Text("月を選択")},text={
        Column { OutlinedTextField(input,{input=it},label={Text("月（YYYY-MM）")}); error?.let { Text(it) } }
    },confirmButton={TextButton(onClick={try { jp.local.okozukai.model.month(input); select(input); choosing=false } catch(_:Exception) { error="1900-01〜2199-12を指定してください" }}) {Text("移動")}},
        dismissButton={TextButton(onClick={choosing=false}) {Text("キャンセル")}})
}
@Composable private fun History(data: jp.local.okozukai.data.repository.MonthData,rows: List<Entry>,hasMore: Boolean,busy: Boolean,filter:String?,clearFilter:()->Unit,more:()->Unit,edit:(Entry)->Unit) {
    val names=data.categories.associate { it.id to it.name }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp,0.dp,16.dp,96.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item { Text("収入 ${yen(data.totals.income)}　支出 ${yen(data.totals.expense)}"); Text("収支 ${yen(data.totals.income-data.totals.expense)}") }
        if(filter!=null) item { Text("絞り込み：${names[filter] ?: "カテゴリ"}"); Text("絞り込み結果 ${yen(data.categoryTotals.find { it.categoryId==filter }?.total ?: 0)}"); TextButton(onClick=clearFilter,enabled=!busy) { Text("絞り込みを解除") } }
        if(rows.isEmpty() && !busy) item { Text("まだ記録がありません") }
        var previous: Long?=null
        rows.forEach { entry ->
            if(previous!=entry.dateEpochDay) {
                val day=data.days.find { it.dateEpochDay==entry.dateEpochDay }
                item(key="day-${entry.dateEpochDay}") { Text(LocalDate.ofEpochDay(entry.dateEpochDay).toString(),style=MaterialTheme.typography.titleMedium)
                    Text("日計：収入 ${yen(day?.income ?: 0)}／支出 ${yen(day?.expense ?: 0)}",style=MaterialTheme.typography.bodySmall) }
                previous=entry.dateEpochDay
            }
            item(key=entry.id) { Card(Modifier.fillMaxWidth().clickable(enabled=!busy) {edit(entry)}) {
                Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text(names[entry.categoryId] ?: "カテゴリ"); if(entry.note.isNotEmpty()) Text(entry.note) }
                    Text((if(entry.kind=="INCOME") "収入＋" else "支出−")+yen(entry.amountYen))
                }
            } }
        }
        if(hasMore) item { TextButton(onClick=more,enabled=!busy) {Text("続きを読む")} }
    }
}
@Composable private fun Summary(data: jp.local.okozukai.data.repository.MonthData,select:(String)->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("月の支出 ${yen(data.totals.expense)}",style=MaterialTheme.typography.titleLarge)
        Text("棒はカテゴリ間の比較、％は支出全体に占める割合です")
        if(data.totals.expense==0L) Text("この月の支出はありません")
        val max=data.categoryTotals.maxOfOrNull { it.total } ?: 1L
        data.categoryTotals.forEach { row ->
            val name=data.categories.find { it.id==row.categoryId }?.name ?: "カテゴリ"
            TextButton(onClick={select(row.categoryId)}) { Text("$name ${yen(row.total)}（${String.format(Locale.JAPAN,"%.1f",row.total.toDouble()/data.totals.expense*100)}％）") }
            LinearProgressIndicator(progress={row.total.toFloat()/max},modifier=Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(80.dp))
    }
}
private fun budgetText(budget:Long?,spent:Long):String {
    if(budget==null) return "予算未設定／実績 ${yen(spent)}"
    val left=budget-spent
    return "予算 ${yen(budget)}／実績 ${yen(spent)}／"+if(left<0) "${yen(-left)}超過" else "残り ${yen(left)}"
}
@Composable private fun BudgetScreen(data: jp.local.okozukai.data.repository.MonthData,month:String,busy:Boolean,edit:()->Unit,copy:()->Unit) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("予算は設定した上限です。現金や口座の残高ではありません。")
        Text("月全体"); Text(budgetText(data.budget?.amountYen,data.totals.expense))
        data.categories.filter {it.kind=="EXPENSE"}.forEach { c ->
            Text(c.name); Text(budgetText(data.budgets.find {it.categoryId==c.id}?.amountYen,data.categoryTotals.find {it.categoryId==c.id}?.total ?: 0))
        }
        val sum=data.budgets.sumOf {it.amountYen}
        if(data.budget!=null && sum>data.budget.amountYen) Text("カテゴリ予算の合計が月全体より${yen(sum-data.budget.amountYen)}多くなっています")
        Button(onClick=edit,enabled=!busy) {Text("予算を編集")}
        OutlinedButton(onClick={confirm=true},enabled=!busy && month>"1900-01") {Text("前月からコピー")}
        Spacer(Modifier.height(80.dp))
    }
    if(confirm) Confirm("予算の置き換え","${month}の現在の設定${data.budgets.size+if(data.budget==null) 0 else 1}件を前月の設定で置き換えます。前月にない設定は未設定になります。",busy,{confirm=false}) {confirm=false; copy()}
}
@Composable private fun Goals(goals:List<Goal>,busy:Boolean,add:()->Unit,edit:(Goal)->Unit) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp,0.dp,16.dp,96.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text("収支とは別に、貯めた額を入力します。合計は資産額ではありません。"); Button(onClick=add,enabled=!busy) {Text("目標を追加")} }
        if(goals.isEmpty()) item {Text("まだ目標がありません")}
        items(goals,key={it.id}) { goal -> Card(Modifier.fillMaxWidth().clickable(enabled=!busy) {edit(goal)}) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(goal.name,style=MaterialTheme.typography.titleMedium)
                Text("現在の積立額 ${yen(goal.savedAmountYen)}／目標 ${yen(goal.targetAmountYen)}")
                Text(if(goal.savedAmountYen>=goal.targetAmountYen) "達成" else "${String.format(Locale.JAPAN,"%.1f",goal.savedAmountYen.toDouble()/goal.targetAmountYen*100)}％")
                LinearProgressIndicator(progress={(goal.savedAmountYen.toFloat()/goal.targetAmountYen).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
            }
        } }
    }
}
@Composable private fun BackupScreen(vm:LedgerViewModel,state:ScreenState) {
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {it?.let(vm::export)}
    val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {it?.let(vm::inspect)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("バックアップ",style=MaterialTheme.typography.titleLarge)
        Text("収支・カテゴリ・予算・目標をJSONで書き出します。暗号化されません。保存先と保管方法に注意してください。アプリ内から外部へ自動送信しません。")
        Text("アプリ削除や端末故障に備え、定期的に手動で書き出してください。クラウド保存先の通信は提供元に従います。")
        Button(onClick={export.launch("okozukai-backup-"+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))+".json")},enabled=!state.busy) {Text("現在のデータを書き出す")}
        OutlinedButton(onClick={open.launch(arrayOf("application/json","text/plain","application/octet-stream"))},enabled=!state.busy) {Text("ファイルから復元")}
        Text("開発版 0.1.0／ローカル保存")
        state.preview?.let { (b,s) ->
            Text("作成日時：${b.exportedAt}\nカテゴリ ${s.categories.size}件・記録 ${s.entries.size}件\n月予算 ${s.months.size}件・カテゴリ予算 ${s.budgets.size}件・目標 ${s.goals.size}件")
            if(s.entries.isEmpty() && s.months.isEmpty() && s.budgets.isEmpty() && s.goals.isEmpty()) Text("記録・予算・目標が全件0になります",color=MaterialTheme.colorScheme.error)
            Text("現在の記録・予算・目標を、このファイルの内容に置き換えます。必要なら上のボタンで現在のデータを書き出してください。")
            var confirm by remember {mutableStateOf(false)}
            Button(onClick={confirm=true},enabled=!state.busy) {Text("この内容で置き換える")}
            TextButton(onClick=vm::dismissPreview,enabled=!state.busy) {Text("復元をキャンセル")}
            if(confirm) Confirm("全体の置き換え","現在の記録・予算・目標を、このファイルの内容に置き換えます。",state.busy,{confirm=false}) {confirm=false; vm.restore()}
        }
    }
}
@Composable private fun Confirm(title:String,text:String,busy:Boolean,dismiss:()->Unit,accept:()->Unit) {
    AlertDialog(onDismissRequest={if(!busy) dismiss()},title={Text(title)},text={Text(text)},
        confirmButton={TextButton(onClick=accept,enabled=!busy) {Text("確認して実行")}},
        dismissButton={TextButton(onClick=dismiss,enabled=!busy) {Text("キャンセル")}})
}
@Composable private fun EditorFrame(title:String,busy:Boolean,dirty:Boolean,close:()->Unit,content:@Composable ColumnScope.()->Unit) {
    var discard by remember {mutableStateOf(false)}
    val requestClose={ if(!busy) {if(dirty) discard=true else close()} }
    BackHandler(enabled=true) {requestClose()}
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(title,style=MaterialTheme.typography.titleLarge)
        content()
        OutlinedButton(onClick=requestClose,enabled=!busy) {Text("キャンセル")}
        Spacer(Modifier.height(24.dp))
    }
    if(discard) Confirm("変更の破棄","入力途中の変更を破棄しますか？",busy,{discard=false}) {discard=false; close()}
}
@Composable private fun MoneyField(value:String,change:(String)->Unit,label:String,busy:Boolean,zero:Boolean=false,optional:Boolean=false) {
    var touched by rememberSaveable {mutableStateOf(false)}
    val error=if(touched && !(optional && value.isBlank())) runCatching {amount(value,zero)}.exceptionOrNull()?.message else null
    OutlinedTextField(value,{touched=true; change(it)},label={Text(label)},enabled=!busy,singleLine=true,
        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),isError=error!=null,
        supportingText={if(error!=null) Text(error)},modifier=Modifier.fillMaxWidth())
}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun EntryEditor(old:Entry?,categories:List<Category>,busy:Boolean,generation:Long,
    save:(String?,String,String,String,String,String,Long)->Unit,delete:(String,Long)->Unit,close:()->Unit) {
    val expected=rememberSaveable {generation}
    var kind by rememberSaveable {mutableStateOf(old?.kind ?: "EXPENSE")}
    var money by rememberSaveable {mutableStateOf(old?.amountYen?.toString() ?: "")}
    var category by rememberSaveable {mutableStateOf(old?.categoryId ?: "")}
    var day by rememberSaveable {mutableStateOf(old?.let {LocalDate.ofEpochDay(it.dateEpochDay).toString()} ?: LocalDate.now().toString())}
    var note by rememberSaveable {mutableStateOf(old?.note ?: "")}
    var attempted by rememberSaveable {mutableStateOf(false)}
    var removing by remember {mutableStateOf(false)}
    val context=LocalContext.current
    val valid=runCatching {amount(money); date(day); cleanText(note,0,200); require(category.isNotEmpty()); true}.getOrDefault(false)
    val dirty=money!=(old?.amountYen?.toString() ?: "") || category!=(old?.categoryId ?: "") || note!=(old?.note ?: "") || kind!=(old?.kind ?: "EXPENSE") || day!=(old?.let {LocalDate.ofEpochDay(it.dateEpochDay).toString()} ?: LocalDate.now().toString())
    EditorFrame(if(old==null) "記録を追加" else "記録を編集",busy,dirty,close) {
        Row { listOf("EXPENSE" to "支出","INCOME" to "収入").forEach { (value,label) -> FilterChip(selected=kind==value,onClick={kind=value; category=""},label={Text(label)},enabled=!busy) } }
        MoneyField(money,{money=it},"金額（円）",busy)
        Text("カテゴリ")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            categories.filter {it.kind==kind && (it.isActive || it.id==old?.categoryId)}.forEach { c ->
                FilterChip(selected=category==c.id,onClick={category=c.id},label={Text(c.name)},enabled=!busy)
            }
        }
        if(attempted && category.isEmpty()) Text("カテゴリを選択してください",color=MaterialTheme.colorScheme.error)
        OutlinedButton(enabled=!busy,onClick={
            val d=runCatching {date(day)}.getOrDefault(LocalDate.now())
            DatePickerDialog(context,{_,y,m,v -> day=LocalDate.of(y,m+1,v).toString()},d.year,d.monthValue-1,d.dayOfMonth).show()
        }) {Text("日付を選ぶ")}
        val dayError=runCatching {date(day)}.exceptionOrNull()?.message
        OutlinedTextField(day,{day=it},label={Text("日付（YYYY-MM-DD）")},enabled=!busy,singleLine=true,isError=dayError!=null,supportingText={if(dayError!=null) Text(dayError)})
        val noteError=runCatching {cleanText(note,0,200)}.exceptionOrNull()?.message
        OutlinedTextField(note,{note=it},label={Text("メモ（任意）")},enabled=!busy,isError=noteError!=null,supportingText={if(noteError!=null) Text(noteError)})
        Button(onClick={attempted=true; if(valid) save(old?.id,kind,money,category,day,note,expected)},enabled=!busy) {Text(if(busy) "保存中" else "保存")}
        if(attempted && !valid) Text("入力内容を確認してください",color=MaterialTheme.colorScheme.error)
        if(old!=null) TextButton(onClick={removing=true},enabled=!busy) {Text("この記録を削除")}
    }
    if(removing && old!=null) Confirm("記録の削除","${LocalDate.ofEpochDay(old.dateEpochDay)}の${yen(old.amountYen)}を削除します。",busy,{removing=false}) {removing=false; delete(old.id,expected)}
}
@Composable private fun GoalEditor(old:Goal?,busy:Boolean,generation:Long,save:(String?,String,String,String,Long)->Unit,delete:(String,Long)->Unit,close:()->Unit) {
    val expected=rememberSaveable {generation}
    var name by rememberSaveable {mutableStateOf(old?.name ?: "")}
    var target by rememberSaveable {mutableStateOf(old?.targetAmountYen?.toString() ?: "")}
    var saved by rememberSaveable {mutableStateOf(old?.savedAmountYen?.toString() ?: "0")}
    var error by remember {mutableStateOf<String?>(null)}
    var removing by remember {mutableStateOf(false)}
    val dirty=name!=(old?.name ?: "") || target!=(old?.targetAmountYen?.toString() ?: "") || saved!=(old?.savedAmountYen?.toString() ?: "0")
    EditorFrame(if(old==null) "目標を追加" else "目標を編集",busy,dirty,close) {
        OutlinedTextField(name,{name=it},label={Text("目標名")},enabled=!busy)
        MoneyField(target,{target=it},"目標額（円）",busy)
        MoneyField(saved,{saved=it},"現在の積立額（円）",busy,zero=true)
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        Button(onClick={try {cleanText(name,1,40); amount(target); amount(saved,true); save(old?.id,name,target,saved,expected)} catch(e:IllegalArgumentException) {error=e.message}},enabled=!busy) {Text("目標を保存")}
        if(old!=null) TextButton(onClick={removing=true},enabled=!busy) {Text("目標を削除")}
    }
    if(removing && old!=null) Confirm("目標の削除","「${old.name}」を削除します。収支の記録には影響しません。",busy,{removing=false}) {removing=false; delete(old.id,expected)}
}
@Composable private fun BudgetEditor(data:jp.local.okozukai.data.repository.MonthData,busy:Boolean,generation:Long,save:(String,Map<String,String>,Long)->Unit,close:()->Unit) {
    val expected=rememberSaveable {generation}
    var overall by rememberSaveable {mutableStateOf(data.budget?.amountYen?.toString() ?: "")}
    val categories=data.categories.filter {it.kind=="EXPENSE"}
    val initial=categories.associate {c -> c.id to (data.budgets.find {it.categoryId==c.id}?.amountYen?.toString() ?: "")}
    var values by rememberSaveable {mutableStateOf(HashMap(initial))}
    var error by remember {mutableStateOf<String?>(null)}
    EditorFrame("予算を編集",busy,overall!=(data.budget?.amountYen?.toString() ?: "") || values!=initial,close) {
        Text("空欄は未設定、0は0円の上限です。月全体とカテゴリは独立した上限です。")
        MoneyField(overall,{overall=it},"月全体（円）",busy,zero=true,optional=true)
        categories.forEach {c -> MoneyField(values[c.id] ?: "",{values=HashMap(values).apply {put(c.id,it)}},c.name+"（円）",busy,zero=true,optional=true) }
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        Button(onClick={try { (values.values+overall).filter {it.isNotBlank()}.forEach {amount(it,true)}; save(overall,values,expected) } catch(e:IllegalArgumentException) {error=e.message}},enabled=!busy) {Text("予算を保存")}
    }
}
