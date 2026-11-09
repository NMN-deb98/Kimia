package ir.kimia.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.kimia.app.ChatViewModel
import ir.kimia.app.data.ChatMode
import ir.kimia.app.data.OnlineConfig
import ir.kimia.app.data.Providers

@Composable
fun SettingsScreen(vm: ChatViewModel) {
    val saved by vm.config.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val test by vm.testResult.collectAsStateWithLifecycle()

    var provider by remember { mutableStateOf(saved.providerId) }
    var base by remember { mutableStateOf(saved.baseUrl) }
    var model by remember { mutableStateOf(saved.model) }
    var key by remember { mutableStateOf(saved.apiKey) }
    var showKey by remember { mutableStateOf(false) }
    val ltr = TextStyle(textDirection = TextDirection.Ltr)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("روش پاسخ‌دهی", fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeButton("خودکار", mode == ChatMode.AUTO, Modifier.weight(1f)) { vm.setMode(ChatMode.AUTO) }
            ModeButton("فقط آفلاین", mode == ChatMode.OFFLINE, Modifier.weight(1f)) { vm.setMode(ChatMode.OFFLINE) }
            ModeButton("فقط آنلاین", mode == ChatMode.ONLINE, Modifier.weight(1f)) { vm.setMode(ChatMode.ONLINE) }
        }
        Text(
            when (mode) {
                ChatMode.AUTO -> "اگر اینترنت و کلید داشته باشی آنلاین (باکیفیت‌تر) جواب می‌دهد. اگر اینترنت نبود یا سرویس خطا داد، با مدل آفلاین جواب می‌دهد."
                ChatMode.OFFLINE -> "همیشه با مدل روی گوشی؛ هیچ پیامی از گوشی بیرون نمی‌رود."
                ChatMode.ONLINE -> "همیشه با سرویس آنلاین. اگر اینترنت یا سرویس مشکل داشته باشد، خطا می‌دهد."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("سرویس آنلاین رایگان", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Providers.all.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { p ->
                    ModeButton(p.title, provider == p.id, Modifier.weight(1f)) {
                        provider = p.id
                        if (p.id != "custom") {
                            base = p.baseUrl
                            model = p.model
                        }
                    }
                }
            }
        }
        val info = Providers.byId(provider)
        if (info.keySite.isNotEmpty()) {
            Text(
                "کلید رایگان را از این سایت بگیر (ثبت‌نام با ایمیل، بدون کارت بانکی): ${info.keySite}",
                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
            )
        }

        OutlinedTextField(
            value = key,
            onValueChange = { key = it.trim() },
            label = { Text("کلید API") },
            singleLine = true,
            textStyle = ltr,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "پنهان کردن کلید" else "نمایش کلید") }
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text("نام مدل") },
            singleLine = true,
            textStyle = ltr,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = base,
            onValueChange = { base = it },
            label = { Text("آدرس سرویس (سازگار با OpenAI)") },
            singleLine = true,
            textStyle = ltr,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.saveConfig(OnlineConfig(provider, base, model, key)) }) { Text("ذخیره") }
            OutlinedButton(onClick = { vm.testOnline(OnlineConfig(provider, base, model, key)) }) { Text("آزمایش اتصال") }
        }
        test?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = if (it.startsWith("✗")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }

        Text(
            "نکته‌ها:\n" +
                "• در حالت آنلاین، پیام‌هایت به سرور همان سرویس فرستاده می‌شود و سقف رایگان دارد.\n" +
                "• کلید فقط روی همین گوشی و به‌صورت رمزگذاری‌شده ذخیره می‌شود.\n" +
                "• ممکن است بعضی از این سرویس‌ها از ایران بدون فیلترشکن باز نشوند؛ «آزمایش اتصال» همین را نشان می‌دهد.\n" +
                "• نام مدل‌های رایگان گاهی عوض می‌شود. اگر خطای ۴۰۴ دیدی، نام مدل را از سایت سرویس به‌روز کن.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModeButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(text, maxLines = 1) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(text, maxLines = 1) }
    }
}
