package com.example.tgclient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tgclient.data.TelegramAccountManager
import com.example.tgclient.model.AuthState
import kotlinx.coroutines.delay

@Composable
fun AuthScreen(state: AuthState, viewModel: ChatwaveViewModel, accountManager: TelegramAccountManager, activeAccountId: String) {
    val accounts by accountManager.accounts.collectAsStateWithLifecycle()
    var input by rememberSaveable(activeAccountId) { mutableStateOf("") }
    var secondInput by rememberSaveable(activeAccountId) { mutableStateOf("") }
    var resendNonce by rememberSaveable(activeAccountId) { mutableStateOf(0) }
    var remainingSeconds by rememberSaveable(activeAccountId) { mutableStateOf(0) }
    var phoneInput by rememberSaveable(activeAccountId) { mutableStateOf("") }
    var showPhoneEditor by rememberSaveable(activeAccountId) { mutableStateOf(false) }
    var showRemoveDialog by rememberSaveable(activeAccountId) { mutableStateOf(false) }
    val verification by viewModel.verification.collectAsStateWithLifecycle()
    val authStep = when (state) {
        AuthState.WaitPhoneNumber -> "phone"
        AuthState.WaitCode -> "code"
        AuthState.WaitPassword -> "password"
        AuthState.WaitRegistration -> "registration"
        AuthState.WaitEmailAddress -> "email"
        AuthState.WaitEmailCode -> "email_code"
        is AuthState.Error -> "error"
        else -> "loading"
    }
    LaunchedEffect(activeAccountId, authStep) {
        // Never carry a phone number into the verification-code field. The
        // same composable is reused when TDLib advances auth state and when
        // the user switches between account repositories.
        if (authStep != "phone") input = ""
        if (authStep != "registration") secondInput = ""
    }
    LaunchedEffect(activeAccountId, authStep, verification.timeoutSeconds, resendNonce) {
        if (authStep != "code") {
            remainingSeconds = 0
            return@LaunchedEffect
        }
        remainingSeconds = verification.timeoutSeconds.takeIf { it > 0 } ?: if (resendNonce > 0) 5 else 0
        while (remainingSeconds > 0) {
            delay(1000)
            remainingSeconds -= 1
        }
    }
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Avatar("Chatwave", size = 82.dp)
            Text("Chatwave", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 14.dp))
            Text("Unofficial Telegram client", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            if (accounts.size > 1) {
                val currentIndex = accounts.indexOfFirst { it.id == activeAccountId }.coerceAtLeast(0)
                val nextAccount = accounts[(currentIndex + 1) % accounts.size]
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(accounts[currentIndex].label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { accountManager.switchAccount(nextAccount.id) }) { Text("Switch account") }
                    }
                    TextButton(onClick = { showRemoveDialog = true }) { Text("Remove this account") }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(
                        when (state) {
                            AuthState.WaitCode, AuthState.WaitEmailCode -> "Enter verification code"
                            AuthState.WaitPassword -> "Two-step verification"
                            AuthState.WaitRegistration -> "Create your account"
                            else -> "Log in with your Telegram phone number"
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        when (state) {
                            AuthState.WaitCode -> "We sent a code to your Telegram app or by SMS."
                            AuthState.WaitEmailCode -> "Enter the code sent to your email address."
                            AuthState.WaitPassword -> "Your account has two-step verification enabled."
                            AuthState.WaitRegistration -> "Choose the name people will see on Telegram."
                            else -> "We will send a verification code to your Telegram app or by SMS."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
                    )
                    val canChangePhone = state != AuthState.WaitPhoneNumber &&
                        state != AuthState.Loading &&
                        state != AuthState.LoggingOut &&
                        state != AuthState.MissingConfiguration &&
                        state != AuthState.Ready
                    if (showPhoneEditor) {
                        Text("Use a different phone number for this account.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        AuthField("New phone number", "+1 555 123 4567", phoneInput, KeyboardType.Phone, "Send code", { phoneInput = it }) {
                            input = ""
                            showPhoneEditor = false
                            viewModel.changeAuthenticationPhoneNumber(phoneInput.trim())
                        }
                        TextButton(onClick = { showPhoneEditor = false }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                    } else {
                        when (state) {
                            AuthState.Loading, AuthState.LoggingOut -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                            AuthState.MissingConfiguration -> {
                                Text("Telegram API credentials are missing.", color = MaterialTheme.colorScheme.error)
                                Text("Add telegram.apiId and telegram.apiHash to local.properties, then rebuild.")
                            }
                            AuthState.WaitPhoneNumber -> AuthField("Phone number", "+1 555 123 4567", input, KeyboardType.Phone, "Continue", { input = it }) { viewModel.submitPhone(input.trim()) }
                            AuthState.WaitCode -> {
                                AuthField("Verification code", "12345", input, KeyboardType.Number, "Verify", { input = it }) { viewModel.submitCode(input.trim()) }
                                TextButton(
                                    onClick = {
                                        resendNonce += 1
                                        viewModel.resendCode()
                                    },
                                    enabled = remainingSeconds == 0,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(if (remainingSeconds > 0) "Resend code in ${remainingSeconds}s" else "Resend code")
                                }
                            }
                            AuthState.WaitPassword -> AuthField("Password", "Two-step password", input, KeyboardType.Password, "Sign in", { input = it }) { viewModel.submitPassword(input) }
                            AuthState.WaitRegistration -> {
                                AuthField("First name", "", input, KeyboardType.Text, null, { input = it })
                                AuthField("Last name (optional)", "", secondInput, KeyboardType.Text, "Create account", { secondInput = it }) { viewModel.register(input.trim(), secondInput.trim()) }
                            }
                            AuthState.WaitEmailAddress -> AuthField("Email address", "name@example.com", input, KeyboardType.Email, "Continue", { viewModel.submitEmail(input.trim()) })
                            AuthState.WaitEmailCode -> AuthField("Email verification code", "12345", input, KeyboardType.Number, "Verify", { viewModel.submitEmailCode(input.trim()) })
                            is AuthState.Error -> {
                                Text(state.message, color = MaterialTheme.colorScheme.error)
                                AuthField("Phone number", "+1 555 123 4567", input, KeyboardType.Phone, "Try again", { input = it }) { viewModel.submitPhone(input.trim()) }
                            }
                            AuthState.Ready -> Unit
                        }
                        if (canChangePhone) {
                            TextButton(
                                onClick = {
                                    phoneInput = ""
                                    showPhoneEditor = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Change phone number") }
                        }
                    }
                }
            }
            Text("By continuing, you agree to use Telegram through its official API.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
        }
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text("Remove account?") },
            text = { Text("Remove this unverified account from Chatwave on this device? Your Telegram account itself will not be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveDialog = false
                    accountManager.removeAccount(activeAccountId)
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { showRemoveDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AuthField(
    label: String,
    placeholder: String,
    value: String,
    keyboardType: KeyboardType,
    buttonLabel: String?,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    )
    if (buttonLabel != null) Button(onClick = onSubmit, enabled = value.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(buttonLabel) }
}
