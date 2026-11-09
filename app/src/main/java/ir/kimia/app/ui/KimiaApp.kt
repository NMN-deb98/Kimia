package ir.kimia.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.kimia.app.ChatViewModel
import ir.kimia.app.Status

private enum class Screen { Chat, Models }

@Composable
fun KimiaApp(vm: ChatViewModel = viewModel()) {
    var screen by rememberSaveable { mutableStateOf(Screen.Chat) }
    val status by vm.status.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()

    // keep the phone awake while a model is downloading so the download is not cut off
    val view = LocalView.current
    val downloading = downloads.values.any { it.error == null }
    LaunchedEffect(downloading) { view.keepScreenOn = downloading }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                if (screen == Screen.Chat) "کیمیا" else "مدل‌ها",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (screen == Screen.Chat) {
                    TextButton(
                        onClick = { vm.newChat() },
                        enabled = status !is Status.Loading && status !is Status.Generating,
                    ) { Text("گفتگوی جدید") }
                    TextButton(onClick = { screen = Screen.Models }) { Text("مدل‌ها") }
                } else {
                    TextButton(onClick = { screen = Screen.Chat }) { Text("بازگشت") }
                }
            }
        }
        when (screen) {
            Screen.Chat -> ChatScreen(vm, onOpenModels = { screen = Screen.Models })
            Screen.Models -> ModelsScreen(vm, onModelChosen = { screen = Screen.Chat })
        }
    }
}
