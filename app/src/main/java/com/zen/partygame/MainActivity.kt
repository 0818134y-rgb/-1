package com.zen.partygame

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.zen.partygame.ui.AppViewModel
import com.zen.partygame.ui.FindingScreen
import com.zen.partygame.ui.OnlineJoinScreen
import com.zen.partygame.ui.LocalSetupScreen
import com.zen.partygame.ui.PartyTheme
import com.zen.partygame.ui.RoomScreen
import com.zen.partygame.ui.TitleScreen
import com.zen.partygame.ui.Ui

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private val admin: com.zen.partygame.ui.AdminViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm.ads.init(this)
        if (savedInstanceState == null) handleInvite(intent)
        setContent {
            val adsReady by vm.ads.ready.collectAsState()
            androidx.compose.runtime.CompositionLocalProvider(com.zen.partygame.ui.LocalAdsReady provides adsReady) {
            PartyTheme {
                val ui by vm.ui.collectAsState()
                val snack = remember { SnackbarHostState() }
                LaunchedEffect(Unit) { vm.messages.collect { snack.showSnackbar(it) } }
                if (BuildConfig.ADMIN) LaunchedEffect(Unit) { admin.messages.collect { snack.showSnackbar(it) } }
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = { SnackbarHost(snack) },
                ) { pad ->
                    Box(Modifier.fillMaxSize().padding(pad)) {
                        if (BuildConfig.ADMIN) com.zen.partygame.ui.AdminApp(admin, vm)
                        else when (val s = ui) {
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
        }
    }

    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); setIntent(intent); handleInvite(intent) }
    /** 招待リンク（https://wachapa.com/join/?room=123456）で開かれたら、その部屋に入る */
    private fun handleInvite(i: android.content.Intent?) {
        if (BuildConfig.ADMIN || i?.action != android.content.Intent.ACTION_VIEW) return
        AppViewModel.inviteCode(i.data)?.let { vm.openInvite(it) }
    }

    // BGM: 裏に回ったら止める（全画面広告が出ている間も止まる）
    override fun onResume() { super.onResume(); com.zen.partygame.ui.Music.onForeground(true) }
    override fun onPause() { com.zen.partygame.ui.Music.onForeground(false); super.onPause() }
}
