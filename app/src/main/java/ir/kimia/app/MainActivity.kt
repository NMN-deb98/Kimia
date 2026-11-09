package ir.kimia.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ir.kimia.app.ui.KimiaApp
import ir.kimia.app.ui.KimiaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KimiaTheme {
                KimiaApp()
            }
        }
    }
}
