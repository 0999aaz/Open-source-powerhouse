package com.example.ghtrending

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ghtrending.data.remote.UpgradeEventBus
import com.example.ghtrending.ui.component.ForceUpgradeDialog
import com.example.ghtrending.ui.navigation.GhTrendingNavHost
import com.example.ghtrending.ui.screen.openInCustomTabs
import com.example.ghtrending.ui.theme.GhTrendingTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var upgradeEventBus: UpgradeEventBus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GhTrendingTheme {
                val upgradePrompt by upgradeEventBus.upgradeState.collectAsStateWithLifecycle()
                GhTrendingNavHost()
                upgradePrompt?.let { prompt ->
                    ForceUpgradeDialog(
                        message = prompt.message,
                        onUpgradeClick = {
                            if (prompt.downloadUrl.isNotBlank()) {
                                openInCustomTabs(this@MainActivity, prompt.downloadUrl)
                            }
                        },
                    )
                }
            }
        }
    }
}
