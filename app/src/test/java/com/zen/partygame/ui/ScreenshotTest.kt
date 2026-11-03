package com.zen.partygame.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import android.graphics.Canvas
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.zen.partygame.game.Engine
import com.zen.partygame.game.GameType
import com.zen.partygame.game.Intent
import com.zen.partygame.game.Screen
import com.zen.partygame.game.Settings
import com.zen.partygame.game.ArenaCard
import com.zen.partygame.game.Suit
import com.zen.partygame.game.TopicBank
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

/** 主な画面を画像にして app/build/screens/ に保存する（見た目の確認用） */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w393dp-h852dp-xhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val out = File("build/screens").apply { mkdirs() }
    private var clock = 0L
    private val bank = TopicBank.parse(File("src/main/assets/topics.json").readText())
    private val names = listOf("ゆうき", "さくら", "けんた", "みお", "たける")

    private fun engine(local: Boolean = false, settings: Settings = Settings()): Engine {
        val e = Engine("4821", local, bank, Random(7)) { clock }
        names.forEachIndexed { i, n -> e.join(if (i == 0) "h" else "p$i", n, isHost = i == 0) }
        e.apply("h", Intent.UpdateSettings(settings))
        return e
    }

    @Test fun captureScreens() {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext())
        vm.setName("ゆうき")
        var content by mutableStateOf<Ui>(Ui.Title)
        rule.setContent {
            CompositionLocalProvider(LocalAdsEnabled provides false) {
                PartyTheme {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        when (val s = content) {
                            Ui.Title -> TitleScreen(vm)
                            Ui.LocalSetup -> LocalSetupScreen(vm)
                            is Ui.Finding -> FindingScreen(vm, s)
                            is Ui.OnlineJoin -> OnlineJoinScreen(vm, s)
                            is Ui.Room -> RoomScreen(vm, s)
                        }
                    }
                }
            }
        }
        fun shot(name: String, ui: Ui) {
            content = ui
            rule.waitForIdle()
            // captureToImage は Robolectric で止まることがあるので、画面のViewを直接描く
            val root = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            rule.runOnUiThread { root.draw(Canvas(bmp)) }
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        fun room(e: Engine, id: String) = Ui.Room(e.viewFor(id), android.os.SystemClock.elapsedRealtime())

        shot("01_title", Ui.Title)
        shot("02_find", Ui.Finding(listOf(com.zen.partygame.net.RoomInfo("x", "4821", "ゆうき")), null))

        val e = engine()
        shot("03_lobby_host", room(e, "h"))
        shot("04_lobby_guest", room(e, "p1"))
        e.apply("h", Intent.Start)
        val wolf = e.players.map { it.id }.groupBy { (e.viewFor(it).screen as Screen.WwReveal).word }.values.minBy { it.size }.first()
        e.apply("p2", Intent.ConfirmWord())
        shot("05_ww_reveal", room(e, "p1"))
        e.players.forEach { e.apply(it.id, Intent.ConfirmWord()) }
        clock += 47_000
        shot("06_ww_talk", room(e, "p1"))
        e.apply("h", Intent.EndTalk)
        e.apply("p1", Intent.Vote("p3")); e.apply("p2", Intent.Vote("p3"))
        shot("07_ww_vote", room(e, "p1"))
        e.players.filter { it.id != wolf }.forEach { e.apply(it.id, Intent.Vote(wolf)) }
        e.apply("h", Intent.CloseVote)
        shot("08_ww_guess", room(e, wolf))
        e.apply("h", Intent.Judge(false))
        shot("09_ww_result", room(e, "p1"))

        val ng = engine(settings = Settings(game = GameType.NG_WORD))
        ng.apply("h", Intent.Start)
        ng.apply("p1", Intent.ReportOut("p3"))
        clock += 95_000
        shot("10_ng_play", room(ng, "p1"))
        ng.apply("h", Intent.ReportOut("p1")); ng.apply("h", Intent.ReportOut("p2")); ng.apply("h", Intent.ReportOut("p4"))
        shot("11_ng_result", room(ng, "h"))

        // --- 追加モード ---
        val spyE = engine(settings = Settings(game = GameType.SPY))
        shot("14_lobby_spy", room(spyE, "h"))
        spyE.apply("h", Intent.Start)
        val spyId = spyE.players.first { (spyE.viewFor(it.id).screen as Screen.WwReveal).spy }.id
        shot("15_spy_reveal_spy", room(spyE, spyId))
        shot("16_spy_reveal_place", room(spyE, spyE.players.first { it.id != spyId }.id))
        spyE.players.forEach { spyE.apply(it.id, Intent.ConfirmWord()) }
        spyE.apply("h", Intent.EndTalk)
        spyE.players.filter { it.id != spyId }.forEach { spyE.apply(it.id, Intent.Vote(spyId)) }
        spyE.apply("h", Intent.CloseVote)
        shot("17_spy_guess", room(spyE, spyId))

        val sync = engine(settings = Settings(game = GameType.SYNC))
        sync.apply("h", Intent.Start)
        sync.apply("h", Intent.SyncAnswer("いちご"))
        shot("18_sync_answer", room(sync, "h"))
        listOf("p1" to "イチゴ", "p2" to "トマト", "p3" to "いちご", "p4" to "りんご").forEach { (id, a) -> sync.apply(id, Intent.SyncAnswer(a)) }
        shot("19_sync_reveal", room(sync, "h"))

        val guess = engine(settings = Settings(game = GameType.GUESS))
        guess.apply("h", Intent.Start)
        val asker = (guess.viewFor("h").screen as Screen.GuessPlay).asker
        shot("20_guess_asker", room(guess, asker))
        shot("21_guess_player", room(guess, guess.players.first { it.id != asker && it.id != "h" }.id))
        guess.apply(asker, Intent.GuessSolved(guess.players.first { it.id != asker }.id))
        shot("22_guess_result", room(guess, "h"))

        val q = engine(settings = Settings(game = GameType.QUESTION))
        q.apply("h", Intent.Start)
        q.apply("p1", Intent.QVote("p2"))
        shot("23_q_vote", room(q, "p1"))
        listOf("h" to "p2", "p2" to "p3", "p3" to "p2", "p4" to "h").forEach { (id, t) -> q.apply(id, Intent.QVote(t)) }
        shot("24_q_reveal", room(q, "h"))

        // --- なんでもアリーナ ---
        val ar = engine(settings = Settings(game = GameType.ARENA))
        shot("25_lobby_arena", room(ar, "h"))
        ar.apply("h", Intent.Start)
        val turnId = (ar.viewFor("h").screen as Screen.Arena).turn
        shot("26_arena_turn", room(ar, turnId))
        shot("27_arena_wait", room(ar, ar.players.first { it.id != turnId }.id))
        // 新しいカードを持たせて出す：落とし穴 → 全員じゃんけん（ほかの人には中身が伏せられる）→ 鼻タッチ
        val g = ar.arenaForTest()!!
        val others = ar.players.map { it.id }.filter { it != turnId }
        val pit = ArenaCard(9001, "pitfall", Suit.SUN); g.seats.getValue(turnId).hand += pit
        ar.apply(turnId, Intent.ArenaPlay(pit.uid, others[0]))
        val t2 = (ar.viewFor("h").screen as Screen.Arena).turn
        listOf("coin", "bomb", "dino").forEachIndexed { i, id -> g.seats.getValue(others[1 + i % 3]).front += ArenaCard(9100 + i, id, Suit.MOON) }
        val rps = ArenaCard(9002, "rps_p", Suit.MOON); g.seats.getValue(t2).hand += rps
        ar.apply(t2, Intent.ArenaPlay(rps.uid))
        val watcher = ar.players.map { it.id }.first { it != t2 }
        shot("28_arena_rps_hidden", room(ar, watcher))
        shot("29_arena_rps_owner", room(ar, t2))
        ar.players.forEach { ar.apply(it.id, Intent.ArenaInput("g")) }
        val t3 = (ar.viewFor("h").screen as? Screen.Arena)?.turn
        if (t3 != null) {
            val nose = ArenaCard(9003, "nose", Suit.FLOWER); g.seats.getValue(t3).hand += nose
            ar.apply(t3, Intent.ArenaPlay(nose.uid))
            shot("31_arena_nose_owner", room(ar, t3))
            ar.apply(t3, Intent.ArenaInput("go"))
            shot("32_arena_nose_go", room(ar, ar.players.map { it.id }.first { it != t3 && g.seats.getValue(it).alive }))
            clock += 10_000; ar.tick()
        }
        // 決着させて結果画面
        val t4 = (ar.viewFor("h").screen as? Screen.Arena)?.turn
        if (t4 != null) {
            g.seats.values.filter { it.id != t4 }.forEach { it.alive = false }
            val sw = ArenaCard(9004, "coin", Suit.LEAF); g.seats.getValue(t4).hand += sw; ar.apply(t4, Intent.ArenaPlay(sw.uid))
        }
        if (ar.viewFor("h").screen is Screen.ArenaResult) shot("30_arena_result", room(ar, "h"))

        shot("12_local_setup", Ui.LocalSetup)
        vm.setOnline(true)
        shot("33_title_online", Ui.Title)
        shot("34_online_join", Ui.OnlineJoin(busy = false))
        shot("35_lobby_online", Ui.Room(ar.also { it.apply("h", Intent.BackToLobby) }.viewFor("h"), 0L, online = true))
        vm.setOnline(false)
        val local = engine(local = true)
        local.apply("h", Intent.Start)
        shot("13_local_reveal", room(local, "h"))
    }
}
