package dev.zun.flux

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import dev.zun.flux.ui.debug.DebugScreen
import dev.zun.flux.ui.theme.ZunFluxTheme

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = (application as FluxApp).repository
        setContent {
            ZunFluxTheme {
                DebugScreen(repository = repo)
            }
        }
    }
}
