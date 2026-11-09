package ir.kimia.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.kimia.app.ChatViewModel
import ir.kimia.app.Status
import ir.kimia.app.data.ChatMessage
import ir.kimia.app.data.Role

@Composable
fun ChatScreen(vm: ChatViewModel, onOpenModels: () -> Unit) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val restored by vm.historyRestored.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val lastLen = messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(messages.size, lastLen) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) {
                EmptyState(status, onOpenModels)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                ) {
                    if (restored) {
                        item(key = "restored") {
                            Text(
                                "این گفتگو ذخیره شده است، ولی مدل محتوای قبلی را به خاطر نمی‌آورد.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    items(messages, key = { it.id }) { MessageBubble(it) }
                }
            }
        }

        StatusBar(status, onRetry = { vm.retryLoad() }, onOpenModels = onOpenModels)

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("پیامت را بنویس…") },
                maxLines = 5,
                textStyle = TextStyle(textDirection = TextDirection.Content),
                enabled = status is Status.Ready || status is Status.Generating,
                shape = RoundedCornerShape(20.dp),
            )
            if (status is Status.Generating) {
                Button(
                    onClick = { vm.stop() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("توقف") }
            } else {
                Button(
                    onClick = {
                        vm.send(input)
                        input = ""
                    },
                    enabled = status is Status.Ready && input.isNotBlank(),
                ) { Text("ارسال") }
            }
        }
    }
}

@Composable
private fun EmptyState(status: Status, onOpenModels: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("کیمیا", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            "دستیار هوش مصنوعی شخصی شما، کاملاً روی خود گوشی",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (status is Status.NoModel) {
            Text(
                "برای شروع، یک مدل هوش مصنوعی دانلود کن. فقط یک بار لازم است و بعدش بدون اینترنت کار می‌کند.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp),
            )
            Button(onClick = onOpenModels, modifier = Modifier.padding(top = 12.dp)) { Text("انتخاب مدل") }
        }
    }
}

@Composable
private fun StatusBar(status: Status, onRetry: () -> Unit, onOpenModels: () -> Unit) {
    when (status) {
        is Status.Loading -> Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(status.what, style = MaterialTheme.typography.bodySmall)
        }
        is Status.Error -> Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Text(status.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Row {
                TextButton(onClick = onRetry) { Text("تلاش دوباره") }
                TextButton(onClick = onOpenModels) { Text("مدل‌ها") }
            }
        }
        else -> {}
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.role == Role.USER
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.Start else Arrangement.End,
    ) {
        Box(
            Modifier
                .widthIn(max = 320.dp)
                .background(
                    if (isUser) scheme.primaryContainer else scheme.surfaceVariant,
                    RoundedCornerShape(18.dp),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            SelectionContainer {
                Text(
                    text = msg.text.ifEmpty { "…" },
                    color = if (isUser) scheme.onPrimaryContainer else scheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                )
            }
        }
    }
}
