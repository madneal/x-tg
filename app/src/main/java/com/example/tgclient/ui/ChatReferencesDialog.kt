package com.example.tgclient.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.tgclient.model.ChatReference

@Composable
internal fun ChatReferencesDialog(
    references: List<ChatReference>,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Links and usernames (24h)") },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (loading) CircularProgressIndicator()
                error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
                if (!loading && error == null && references.isEmpty()) Text("No links or usernames found in the last 24 hours.")
                references.forEach { reference ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(reference.value, maxLines = 2)
                            Text(
                                if (reference.kind == ChatReference.Kind.USERNAME) "Username" else "Link",
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { openExternalLink(context, reference.target) }) { Text("Open") }
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(enabled = references.isNotEmpty(), onClick = {
                    clipboard.setText(AnnotatedString(references.joinToString("\n") { it.value }))
                }) { Text("Copy all") }
                TextButton(enabled = !loading, onClick = onRefresh) { Text("Refresh") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}
