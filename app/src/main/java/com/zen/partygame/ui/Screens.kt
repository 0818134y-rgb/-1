package com.zen.partygame.ui

import com.zen.partygame.R
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.annotation.DrawableRes
import android.Manifest
import android.content.Intent as AndroidIntent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.zen.partygame.BuildConfig
import com.zen.partygame.game.MAX_NAME_LEN
import com.zen.partygame.game.MAX_PLAYERS_LOCAL
import com.zen.partygame.game.MIN_PLAYERS
import com.zen.partygame.net.RoomInfo

// ================= 共通部品 =================

/** ChatGPTで作った絵（res/drawable-nodpi）を表示する */
@Composable
fun Art(@DrawableRes id: Int, size: Dp, modifier: Modifier = Modifier) {
    Image(painterResource(id), contentDescription = null, modifier = modifier.size(size), contentScale = ContentScale.Fit)
}

@Composable
fun BigButton(
    text: String, modifier: Modifier = Modifier, enabled: Boolean = true, secondary: Boolean = false,
    @DrawableRes icon: Int? = null, onClick: () -> Unit,
) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().height(if (icon != null) 64.dp else 56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = if (secondary) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface)
        else ButtonDefaults.buttonColors(),
    ) {
        if (icon != null) {
            Art(icon, 40.dp, Modifier.alpha(if (enabled) 1f else 0.45f))
            Spacer(Modifier.width(12.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** 絵＋見出しの行 */
@Composable
fun ArtTitle(@DrawableRes id: Int, text: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onBackground) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Art(id, 44.dp); Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.headlineMedium, color = color)
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) { Column(Modifier.padding(18.dp), content = content) }
}

@Composable
fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** 広告を表示するか（画面の見た目テストでは false にする） */
val LocalAdsEnabled = staticCompositionLocalOf { true }
/** 同意の確認が済んで、広告を出してよい状態か */
val LocalAdsReady = androidx.compose.runtime.compositionLocalOf { false }

/** 画面下のバナー広告（ロビーと結果画面だけ） */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    if (!LocalAdsEnabled.current) {
        Box(modifier.fillMaxWidth().height(60.dp).background(androidx.compose.ui.graphics.Color(0xFF3A3A3A)), contentAlignment = Alignment.Center) { Caption(tr("（バナー広告）")) }
        return
    }
    if (!LocalAdsReady.current) return
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            val widthDp = (ctx.resources.displayMetrics.widthPixels / ctx.resources.displayMetrics.density).toInt()
            AdView(ctx).apply {
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, widthDp))
                adUnitId = BuildConfig.AD_BANNER_ID
                loadAd(AdRequest.Builder().build())
            }
        },
        onRelease = { it.destroy() },
    )
}

/**
 * 近くの端末と通信するための許可（Android の版で必要なものが違う）。
 * 位置情報は機種によって近くの端末を見つけるのに使われるため、どの版でも求める（位置そのものは使わない）
 */
fun nearbyPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 33 -> arrayOf(
        Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.NEARBY_WIFI_DEVICES,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )
    Build.VERSION.SDK_INT >= 31 -> arrayOf(
        Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION,
    )
    else -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
}

// ================= タイトル =================

@Composable
fun TitleScreen(vm: AppViewModel) {
    val name by vm.name.collectAsState()
    val online by vm.online.collectAsState()
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showRules by remember { mutableStateOf(false) }
    var showLang by remember { mutableStateOf(false) }
    val lang by vm.lang.collectAsState()
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) pending?.invoke()
        else vm.notify(tr("「付近のデバイス」の許可がないと、みんなのスマホをつなげません"))
        pending = null
    }
    // オンラインは「付近のデバイス」の許可がいらない
    fun withPermissions(action: () -> Unit) { if (online) action() else { pending = action; launcher.launch(nearbyPermissions()) } }

    val privacyButton by vm.ads.privacyButton.collectAsState()
    Column(Modifier.fillMaxSize()) {
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { LanguageButton(lang) { showLang = true } }
        Art(R.drawable.char_wolf_hello, 190.dp)
        Text(tr("わちゃパ"), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
        Text(tr("ワードウルフ・NGワード ほか"), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(6.dp))
        Caption(tr("8つのパーティーゲーム。全員のスマホで遊べる"), Modifier.padding(bottom = 20.dp))

        // つなぎ方の切り替え（これだけ）
        ModeSwitch(online, vm::setOnline)
        Caption(
            if (online) tr("離れていてもOK。PC版の人とも遊べます（ネットが必要）") else tr("同じ場所にいる人と。ネット不要（Bluetooth・Wi-Fiでつなぐ）"),
            Modifier.padding(top = 6.dp, bottom = 16.dp),
        )

        OutlinedTextField(
            value = name, onValueChange = vm::setName, singleLine = true,
            label = { Text(tr("あなたの名前（{0}文字まで）", MAX_NAME_LEN)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        BigButton(tr("部屋を作る"), enabled = name.isNotBlank(), icon = R.drawable.icon_create) { withPermissions { vm.host() } }
        Spacer(Modifier.height(12.dp))
        BigButton(tr("部屋に参加する"), enabled = name.isNotBlank(), secondary = true, icon = R.drawable.icon_join) {
            if (online) vm.openOnlineJoin() else withPermissions { vm.find() }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { vm.openLocalSetup() },
            modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(18.dp),
        ) {
            Art(R.drawable.icon_pass, 34.dp); Spacer(Modifier.width(10.dp))
            Text(tr("スマホ1台でまわす"))
        }
        if (name.isBlank()) Caption(tr("まず名前を入れてください"), Modifier.padding(top = 10.dp))

        Spacer(Modifier.height(28.dp))
        Row {
            TextButton(onClick = { showRules = true }) { Art(R.drawable.icon_rules, 26.dp); Spacer(Modifier.width(6.dp)); Text(tr("遊び方")) }
            TextButton(onClick = {
                ctx.startActivity(AndroidIntent(AndroidIntent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_URL)))
            }) { Text(tr("プライバシーポリシー")) }
        }
        // ヨーロッパなど同意が必要な地域では、あとから広告の同意を変えられるようにする（ポリシーで必要）
        if (privacyButton) TextButton(onClick = { ctx.findActivity()?.let { vm.ads.showPrivacyOptions(it) } }) { Text(tr("広告の設定")) }
    }
    AdBanner()
    }
    if (showRules) RulesDialog { showRules = false }
    if (showLang) LanguageDialog(lang, onPick = { vm.setLang(it); showLang = false }, onClose = { showLang = false })
}

/** 言語の切り替えボタン（どの言語の人にも分かるよう「🌐 Language」と今の言語を出す） */
@Composable
fun LanguageButton(code: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(14.dp)) {
        Text("🌐 Language · " + (I18n.langs.firstOrNull { it.code == code }?.label ?: code), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun LanguageDialog(code: String, onPick: (String) -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Language / 言語") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                I18n.langs.forEach { l ->
                    val on = l.code == code
                    OutlinedButton(
                        onClick = { onPick(l.code) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(2.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) { Text((if (on) "✓ " else "") + l.label, style = MaterialTheme.typography.titleMedium) }
                }
                Caption(tr("お題やカードは、部屋を作った人の言語になります"))
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("閉じる")) } },
    )
}

/** 「近くで」「オンライン」の切り替え */
@Composable
fun ModeSwitch(online: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NightCard).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(false to tr("📶 近くで"), true to tr("🌐 オンライン")).forEach { (v, label) ->
            val on = online == v
            Box(
                Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onChange(v) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontWeight = FontWeight.Bold, color = if (on) Night else Soft)
            }
        }
    }
}

// ================= オンラインの部屋に入る =================

@Composable
fun OnlineJoinScreen(vm: AppViewModel, state: Ui.OnlineJoin) {
    val last by vm.lastCode.collectAsState()
    var code by remember { mutableStateOf(last) }
    BackHandler { vm.leave() }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ArtTitle(R.drawable.icon_join, tr("部屋コードで参加"))
        Caption(tr("部屋を作った人の画面に出ている{0}けたの数字を入れてください", AppViewModel.ONLINE_CODE_LEN), Modifier.padding(vertical = 8.dp))
        Art(R.drawable.char_wolf_hello, 140.dp, Modifier.padding(vertical = 12.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { v -> code = v.filter { it.isDigit() }.take(AppViewModel.ONLINE_CODE_LEN) },
            singleLine = true, enabled = !state.busy,
            label = { Text(tr("部屋コード")) },
            textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center, letterSpacing = 6.sp),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        if (state.busy) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 14.dp)) {
            CircularProgressIndicator(Modifier.size(24.dp)); Spacer(Modifier.width(14.dp)); Text(tr("つないでいます…"))
        } else BigButton(tr("入る"), enabled = code.length == AppViewModel.ONLINE_CODE_LEN) { vm.joinOnline(code) }
        Spacer(Modifier.weight(1f))
        BigButton(tr("もどる"), secondary = true) { vm.leave() }
    }
}

@Composable
fun RulesDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text(tr("とじる")) } },
        title = { Text(tr("遊び方")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                RuleHead(R.drawable.icon_arena, tr("なんでもアリーナ（一押し）"), MaterialTheme.colorScheme.secondary)
                Text(tr("手番が来たら山札から1枚引き、手札から1枚出して、書かれていることに従います。落とし穴・ばくだん・全員じゃんけん・せーのでゆび・鼻タッチ・言ってはいけない言葉…何が起きるかわかりません。負けたら抜けます。最後の1人になるか、カードの勝利条件（金貨15ポイント・誕生月など）を満たせば勝ち！ 全員が負けることもあります。★のついたスターカードは特に強力。ゲームの最初に「今夜のルール」が1つ追加されます。言葉やしぐさのルールを破った人を見つけたら、その人の「違反！」を押しましょう。"))
                Spacer(Modifier.height(14.dp))
                RuleHead(R.drawable.icon_wordwolf, tr("ワードウルフ"), MaterialTheme.colorScheme.primary)
                Text(tr("全員にお題が配られますが、1人（7人以上なら2人）だけ少し違うお題です。それが「ウルフ」。自分がどちらかは分かりません。話し合って、ウルフだと思う人に投票します。ウルフが最多票なら、ウルフは市民のお題を当てれば逆転勝ち。"))
                Spacer(Modifier.height(14.dp))
                RuleHead(R.drawable.icon_ngword, tr("NGワード"), MaterialTheme.colorScheme.secondary)
                Text(tr("全員に別々のNGワードが付きます。自分のワードだけ見えません。おしゃべりしながら、ほかの人にその人のNGワードを言わせましょう。言った人を見つけたら「言った！」を押します。最後まで残った人の勝ち。"))
                Spacer(Modifier.height(14.dp))
                RuleHead(R.drawable.icon_spy, tr("スパイはだれだ"), MaterialTheme.colorScheme.primary)
                Text(tr("全員に同じ「場所」が配られますが、スパイだけは場所を知りません。順番に質問し合ってスパイを探します。スパイが最多票なら、スパイは12個の候補から場所を当てれば逆転勝ち。"))
                Spacer(Modifier.height(14.dp))
                RuleHead(R.drawable.icon_sync, tr("みんなでシンクロ"), MaterialTheme.colorScheme.primary)
                Text(tr("「赤い食べ物といえば？」などのお題に、全員が自分のスマホで答えます。同じ答えの人が多いほど得点。"))
                Spacer(Modifier.height(14.dp))
                RuleHead(R.drawable.icon_guess, tr("質問で当てろ"), MaterialTheme.colorScheme.primary)
                Text(tr("出題者だけが答えを知っています。ほかの人は「はい・いいえ」で答えられる質問をして、時間内に当てます。当てた人と出題者に1点。"))
                Spacer(Modifier.height(14.dp))
                RuleHead(R.drawable.icon_question, tr("ぶっちゃけ質問"), MaterialTheme.colorScheme.primary)
                Text(tr("「この中で一番〇〇なのは？」に全員が匿名で投票して、結果を発表します。"))
                Spacer(Modifier.height(14.dp))
                RuleHead(R.drawable.icon_draw, tr("お絵かき伝言"), MaterialTheme.colorScheme.primary)
                Text(tr("最初に全員がお題（「ねこが空を飛んでいる」など）を書きます。それが隣の人に回り、絵にします。次の人はその絵だけを見て何の絵か文で答え、さらに次の人がその文を絵に…と全員分くり返します。最後に、お題がどう変わっていったかを1コマずつ発表して笑いましょう。"))
                Spacer(Modifier.height(14.dp))
                RuleHead(R.drawable.icon_join, tr("つなぎ方"), MaterialTheme.colorScheme.onSurface)
                Text(tr("近くで：1人が「部屋を作る」、ほかの人は「部屋に参加する」で同じ部屋番号を選びます。BluetoothとWi-Fiをオンにしてください（Wi-Fiにつながっている必要はありません）。\n\nオンライン：タイトルで「オンライン」に切り替えて部屋を作ると、6けたの部屋コードが出ます。ほかの人（PC版の人も）はそのコードで参加します。話すゲームは、通話アプリをつなぎながら遊びましょう。"))
            }
        },
    )
}

@Composable
private fun RuleHead(@DrawableRes id: Int, text: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
        Art(id, 30.dp); Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

// ================= 部屋さがし =================

@Composable
fun FindingScreen(vm: AppViewModel, state: Ui.Finding) {
    BackHandler { vm.leave() }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        ArtTitle(R.drawable.icon_join, tr("近くの部屋"))
        Caption(tr("ホストの画面に出ている部屋番号を選んでください"), Modifier.padding(vertical = 8.dp))
        if (state.rooms.isEmpty()) {
            Art(R.drawable.char_wolf_hello, 140.dp, Modifier.align(Alignment.CenterHorizontally).padding(top = 20.dp))
            Row(Modifier.padding(vertical = 16.dp).align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Spacer(Modifier.width(14.dp))
                Text(tr("探しています…"))
            }
            Caption(tr("見つからないときは、BluetoothとWi-Fiがオンか、ホストが「部屋を作る」を押しているか確認してください"))
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.rooms, key = { it.endpointId }) { room -> RoomRow(room, state.joining) { vm.join(room) } }
        }
        BigButton(tr("もどる"), secondary = true) { vm.leave() }
    }
}

@Composable
private fun RoomRow(room: RoomInfo, joining: String?, onClick: () -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("部屋 {0}", room.code), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Caption(tr("ホスト：{0}", room.hostName))
            }
            if (joining == room.endpointId) CircularProgressIndicator(Modifier.size(28.dp))
            else Button(onClick = onClick, enabled = joining == null) { Text(tr("入る")) }
        }
    }
}

// ================= 1台モードの準備 =================

@Composable
fun LocalSetupScreen(vm: AppViewModel) {
    val myName by vm.name.collectAsState()
    val names = remember { mutableStateListOf(myName.ifBlank { tr("プレイヤー1") }, tr("プレイヤー2"), tr("プレイヤー3"), tr("プレイヤー4")) }
    BackHandler { vm.leave() }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        ArtTitle(R.drawable.icon_pass, tr("参加する人"))
        Caption(tr("スマホを順番に渡してお題を確認します（{0}〜{1}人）", MIN_PLAYERS, MAX_PLAYERS_LOCAL), Modifier.padding(vertical = 8.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(names) { i, n ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = n, onValueChange = { names[i] = it.take(MAX_NAME_LEN) }, singleLine = true,
                        label = { Text(tr("{0}人目", i + 1)) }, modifier = Modifier.weight(1f),
                    )
                    if (names.size > MIN_PLAYERS) TextButton(onClick = { names.removeAt(i) }) { Text("✕") }
                }
            }
            item {
                if (names.size < MAX_PLAYERS_LOCAL) TextButton(onClick = { names.add(tr("プレイヤー{0}", names.size + 1)) }) { Text(tr("＋ 人を増やす")) }
            }
        }
        BigButton(tr("この人数ではじめる（{0}人）", names.size)) { vm.startLocal(names.map { it.trim() }) }
        Spacer(Modifier.height(10.dp))
        BigButton(tr("もどる"), secondary = true) { vm.leave() }
    }
}

@Composable
fun CenterBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) { content() }
}

@Composable
fun BigWord(text: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary) {
    // 長い文（シンクロのお題・ぶっちゃけ質問など）は少し小さく
    val style = if (text.length > 9) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displayMedium
    Text(text, style = style, color = color, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}
