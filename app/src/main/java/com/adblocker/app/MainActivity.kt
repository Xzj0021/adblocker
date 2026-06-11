package com.adblocker.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.lifecycle.lifecycleScope
import com.adblocker.app.blocklist.BlocklistStore
import com.adblocker.app.data.AppDatabase
import com.adblocker.app.service.VpnStateManager
import com.adblocker.app.ui.screens.HomeScreen
import com.adblocker.app.ui.theme.AdBlockerTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Preload blocklist
        lifecycleScope.launch {
            BlocklistStore.init(this@MainActivity)
        }

        setContent {
            AdBlockerTheme {
                val vpnState by VpnStateManager.state.observeAsState(VpnStateManager.VpnState.IDLE)
                val errorMessage by VpnStateManager.errorMessage.observeAsState(null)

                val db = remember { AppDatabase.getInstance(this@MainActivity) }
                val totalBlocked by db.statsDao().totalBlocked().observeAsState(0L)
                val totalQueries by db.statsDao().totalQueries().observeAsState(0L)

                HomeScreen(
                    vpnState = vpnState,
                    totalBlocked = totalBlocked,
                    totalQueries = totalQueries,
                    errorMessage = errorMessage,
                    onToggleVpn = {
                        if (vpnState == VpnStateManager.VpnState.RUNNING) {
                            VpnStateManager.stop(this@MainActivity)
                        } else {
                            VpnStateManager.prepareAndStart(this@MainActivity)
                        }
                    }
                )
            }
        }
    }

    @Deprecated("Use registerForActivityResult instead")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        VpnStateManager.handleActivityResult(requestCode, resultCode, this)
    }
}
