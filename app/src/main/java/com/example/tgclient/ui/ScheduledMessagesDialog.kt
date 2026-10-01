package com.example.tgclient.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.tgclient.data.ScheduledMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun ScheduleMessageDialog(onDismiss: () -> Unit, onSchedule: suspend (Long) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var time by remember { mutableStateOf(LocalDateTime.now().plusHours(1).withSecond(0).withNano(0)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Schedule message") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Time zone: ${ZoneId.systemDefault().id}")
            OutlinedButton(enabled = !busy, onClick = {
                DatePickerDialog(context, { _, year, month, day -> time = time.withDayOfMonth(1).withYear(year).withMonth(month + 1).withDayOfMonth(day) }, time.year, time.monthValue - 1, time.dayOfMonth).show()
            }) { Text(time.toLocalDate().toString()) }
            OutlinedButton(enabled = !busy, onClick = {
                TimePickerDialog(context, { _, hour, minute -> time = time.withHour(hour).withMinute(minute) }, time.hour, time.minute, true).show()
            }) { Text(time.format(DateTimeFormatter.ofPattern("HH:mm"))) }
            Text("Telegram will send this message at the selected time.")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = {
        TextButton(enabled = !busy, onClick = schedule@{
            if (busy) return@schedule
            busy = true
            error = null
            scope.launch {
                try {
                    onSchedule(time.atZone(ZoneId.systemDefault()).toEpochSecond())
                    onDismiss()
                } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                    error = "No confirmation yet. Check Scheduled messages before retrying."
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message ?: "Scheduling failed. Please retry." }
                finally { busy = false }
            }
        }) { Text(if (busy) "Scheduling…" else "Schedule") }
    }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } })
}

@Composable
internal fun ScheduledMessagesDialog(chatId: Long, viewModel: ChatwaveViewModel, onDismiss: () -> Unit) {
    var messages by remember(chatId) { mutableStateOf<List<ScheduledMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(chatId, refresh) {
        loading = true
        error = null
        try { messages = viewModel.scheduledMessages(chatId) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Unable to load scheduled messages. Please retry." }
        finally { loading = false }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Scheduled messages") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (loading) CircularProgressIndicator()
            if (!loading && error == null && messages.isEmpty()) Text("No scheduled messages")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            messages.forEach { message ->
                Text(message.text, maxLines = 3)
                Text(if (message.sendAt == 0L) "When online" else Instant.ofEpochSecond(message.sendAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try { viewModel.cancelScheduledMessage(chatId, message.id); refresh++ }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { error = "Unable to cancel scheduled message. Please retry." }
                        finally { busy = false }
                    }
                }) { Text("Cancel scheduled message") }
                HorizontalDivider()
            }
            TextButton(enabled = !loading && !busy, onClick = { refresh++ }) { Text("Refresh") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}
