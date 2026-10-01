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
internal fun ScheduleMessageDialog(onDismiss: () -> Unit, onSchedule: suspend (Long, Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var time by remember { mutableStateOf(LocalDateTime.now().plusHours(1).withSecond(0).withNano(0)) }
    var daily by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Schedule message") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Time zone: ${ZoneId.systemDefault().id}")
            OutlinedButton(enabled = !busy, onClick = {
                DatePickerDialog(context, { _, year, month, day -> time = time.withDayOfMonth(1).withYear(year).withMonth(month + 1).withDayOfMonth(day) }, time.year, time.monthValue - 1, time.dayOfMonth).show()
            }) { Text(time.toLocalDate().toString()) }
            OutlinedButton(enabled = !busy, onClick = {
                TimePickerDialog(context, { _, hour, minute -> time = time.withHour(hour).withMinute(minute) }, time.hour, time.minute, true).show()
            }) { Text(time.format(DateTimeFormatter.ofPattern("HH:mm"))) }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = daily, onCheckedChange = { daily = it }, enabled = !busy)
                Text("Repeat daily (24 hours)")
            }
            Text("Saved on this device. No Premium required. Keep this account logged in and allow background activity. Offline, battery restrictions or force-stop can delay sending; reopen the app after force-stop.")
            if (daily) Text("Repeats every 24 hours. Missed days are not sent in a burst. Local time may shift with daylight saving time.")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = {
        TextButton(enabled = !busy, onClick = schedule@{
            if (busy) return@schedule
            busy = true
            error = null
            scope.launch {
                try {
                    onSchedule(time.atZone(ZoneId.systemDefault()).toEpochSecond(), if (daily) 86400 else 0)
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
                if (message.repeatPeriod > 0) Text(if (message.repeatPeriod == 86400) "Repeats daily (24 hours)" else "Repeats every ${message.repeatPeriod / 3600} hours")
                Text(message.status)
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
                }) { Text(if (message.repeatPeriod > 0) "Cancel recurring message" else "Cancel scheduled message") }
                HorizontalDivider()
            }
            TextButton(enabled = !loading && !busy, onClick = { refresh++ }) { Text("Refresh") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}
