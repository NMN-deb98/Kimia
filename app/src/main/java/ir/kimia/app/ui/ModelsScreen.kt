package ir.kimia.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.kimia.app.ChatViewModel
import ir.kimia.app.DownloadState
import ir.kimia.app.Status
import ir.kimia.app.data.ModelCatalog
import ir.kimia.app.data.ModelInfo

private fun mb(bytes: Long) = "${bytes / (1024 * 1024)} MB"

@Composable
fun ModelsScreen(vm: ChatViewModel, onModelChosen: () -> Unit) {
    val installed by vm.installed.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val current by vm.currentModel.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val device = vm.device
    val recommended = ModelCatalog.recommendedFor(device)
    val busy = status is Status.Loading || status is Status.Generating

    var confirmDelete by remember { mutableStateOf<String?>(null) }
    var showUrlDialog by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importModel(uri)
    }

    val catalogNames = ModelCatalog.models.map { it.fileName }.toSet()
    val customInstalled = installed.filter { it !in catalogNames }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        notice?.let { n ->
            item(key = "notice") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(
                        Modifier.padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(n, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { vm.dismissNotice() }) { Text("بستن") }
                    }
                }
            }
        }

        item(key = "device") {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("گوشی شما", fontWeight = FontWeight.Bold)
                    Text(
                        "${device.model} • اندروید (API ${device.androidSdk})",
                        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                    )
                    Text(
                        "RAM: ${device.totalRamMb} MB • فضای خالی: ${device.freeStorageMb} MB • هسته‌ها: ${device.cores}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        recommended?.let { "مدل پیشنهادی برای این گوشی: ${it.name}" }
                            ?: "RAM این گوشی برای کوچک‌ترین مدل فهرست هم کم است؛ ممکن است کند یا ناپایدار باشد.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        items(ModelCatalog.models, key = { it.id }) { m ->
            ModelCard(
                model = m,
                isInstalled = m.fileName in installed,
                isCurrent = current == m.fileName,
                isRecommended = m == recommended,
                ramTooLow = device.totalRamMb < m.minRamMb,
                download = downloads[m.fileName],
                busy = busy,
                onDownload = { vm.startDownload(m.url, m.fileName, m.approxSizeMb) },
                onCancel = { vm.cancelDownload(m.fileName) },
                onDismissError = { vm.dismissDownloadError(m.fileName) },
                onUse = { vm.useModel(m.fileName); onModelChosen() },
                onDelete = { confirmDelete = m.fileName },
            )
        }

        if (customInstalled.isNotEmpty()) {
            item(key = "custom-title") { Text("مدل‌های افزوده‌شده", fontWeight = FontWeight.Bold) }
            items(customInstalled, key = { "c-$it" }) { name ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (current == name) {
                                Text("در حال استفاده", color = MaterialTheme.colorScheme.primary)
                            } else {
                                Button(onClick = { vm.useModel(name); onModelChosen() }, enabled = !busy) { Text("استفاده") }
                            }
                            TextButton(onClick = { confirmDelete = name }, enabled = !busy) { Text("حذف") }
                        }
                    }
                }
            }
        }

        item(key = "add") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("مدل دلخواه", fontWeight = FontWeight.Bold)
                Text(
                    "هر مدل GGUF دیگری را هم می‌توانی اضافه کنی. اگر دانلود مستقیم برایت باز نمی‌شود، فایل را از راه دیگری بگیر و از حافظه‌ی گوشی انتخاب کن.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("افزودن از فایل") }
                    OutlinedButton(onClick = { showUrlDialog = true }) { Text("دانلود از لینک") }
                }
            }
        }
    }

    confirmDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("حذف مدل") },
            text = { Text("فایل مدل از گوشی پاک می‌شود. اگر بخواهی دوباره استفاده کنی باید دوباره دانلودش کنی.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteModel(name); confirmDelete = null }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("انصراف") } },
        )
    }

    if (showUrlDialog) {
        var url by remember { mutableStateOf("") }
        val fileName = url.substringBefore('?').substringAfterLast('/')
        val valid = url.startsWith("https://") && fileName.endsWith(".gguf", ignoreCase = true)
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("دانلود از لینک") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("لینک مستقیم یک فایل .gguf (با https) را وارد کن.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it.trim() },
                        singleLine = true,
                        textStyle = TextStyle(textDirection = TextDirection.Ltr),
                        placeholder = { Text("https://…/model.gguf") },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = valid,
                    onClick = {
                        vm.startDownload(url, fileName.replace(Regex("[^A-Za-z0-9._-]"), "_"))
                        showUrlDialog = false
                    },
                ) { Text("دانلود") }
            },
            dismissButton = { TextButton(onClick = { showUrlDialog = false }) { Text("انصراف") } },
        )
    }
}

@Composable
private fun ModelCard(
    model: ModelInfo,
    isInstalled: Boolean,
    isCurrent: Boolean,
    isRecommended: Boolean,
    ramTooLow: Boolean,
    download: DownloadState?,
    busy: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDismissError: () -> Unit,
    onUse: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(model.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            if (isRecommended) {
                Text("پیشنهاد برای گوشی شما", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
            Text(model.description, style = MaterialTheme.typography.bodyMedium)
            Text(
                "حجم دانلود: حدود ${model.approxSizeMb} MB • حداقل RAM: حدود ${model.minRamMb / 1024 + 1} GB • مجوز: ${model.license}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (ramTooLow) {
                Text(
                    "RAM این گوشی کمتر از حد پیشنهادی است؛ ممکن است مدل بسته شود یا خیلی کند باشد.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            when {
                download != null && download.error != null -> {
                    Text(download.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onDownload) { Text("تلاش دوباره") }
                        TextButton(onClick = onDismissError) { Text("بستن") }
                    }
                }
                download != null -> {
                    if (download.total > 0) {
                        LinearProgressIndicator(
                            progress = { (download.received.toFloat() / download.total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("${mb(download.received)} از ${mb(download.total)}", style = MaterialTheme.typography.bodySmall)
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(mb(download.received), style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = onCancel) { Text("توقف (بعداً ادامه می‌یابد)") }
                }
                isInstalled -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isCurrent) {
                        Text("در حال استفاده", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    } else {
                        Button(onClick = onUse, enabled = !busy) { Text("استفاده") }
                    }
                    TextButton(onClick = onDelete, enabled = !busy) { Text("حذف") }
                }
                else -> Button(onClick = onDownload) { Text("دانلود") }
            }
        }
    }
}
