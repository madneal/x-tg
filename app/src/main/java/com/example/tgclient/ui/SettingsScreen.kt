package com.example.tgclient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tgclient.data.TelegramAccountManager
import com.example.tgclient.model.AccountSummary

private enum class AccountSetting { Profile, PhoneNumber, TwoStepVerification, ActiveSessions }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ChatwaveViewModel,
    accountManager: TelegramAccountManager,
    activeAccountId: String,
    onBack: () -> Unit,
    onAccountChanged: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val accounts by accountManager.accounts.collectAsStateWithLifecycle()
    val activeAccount = accounts.firstOrNull { it.id == activeAccountId }
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    var showLanguageDialog by remember { mutableStateOf(false) }
    var accountToRemove by remember { mutableStateOf<AccountSummary?>(null) }
    var accountSetting by remember { mutableStateOf<AccountSetting?>(null) }
    var profileFirstName by rememberSaveable(activeAccountId) { mutableStateOf("") }
    var profileLastName by rememberSaveable(activeAccountId) { mutableStateOf("") }
    var profileUsername by rememberSaveable(activeAccountId) { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()),
        ) {
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
                ListItem(
                    leadingContent = { Avatar(currentUser?.displayName ?: "Chatwave", size = 58.dp, photoPath = currentUser?.avatarPath) },
                    headlineContent = { Text(activeAccount?.label ?: "Telegram account", style = MaterialTheme.typography.titleLarge) },
                    supportingContent = { Text("Signed in with your Telegram account") },
                )
            }

            SettingsSection("Accounts") {
                accounts.forEach { account ->
                    SettingInfo(
                        icon = Icons.Default.AccountCircle,
                        title = account.label,
                        supporting = if (account.id == activeAccountId) "Currently active" else "Tap to switch account",
                        modifier = Modifier.clickable {
                            accountManager.switchAccount(account.id)
                            onAccountChanged()
                        },
                        trailingContent = if (accounts.size > 1) {
                            {
                                IconButton(onClick = { accountToRemove = account }) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove account", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        } else null,
                    )
                }
                Button(
                    onClick = {
                        accountManager.createAccount()
                        onAccountChanged()
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text("Add account") }
            }

            SettingsSection("Account") {
                SettingInfo(
                    Icons.Default.AccountCircle,
                    "Profile",
                    "Name, username and profile photo",
                    modifier = Modifier.clickable {
                        profileFirstName = currentUser?.firstName ?: currentUser?.displayName.orEmpty()
                        profileLastName = currentUser?.lastName.orEmpty()
                        profileUsername = currentUser?.username.orEmpty()
                        accountSetting = AccountSetting.Profile
                    },
                )
                SettingInfo(
                    Icons.Default.Lock,
                    "Phone number",
                    currentUser?.phoneNumber?.takeIf { it.isNotBlank() }?.let { "+$it" } ?: "Managed by Telegram",
                    modifier = Modifier.clickable { accountSetting = AccountSetting.PhoneNumber },
                )
                SettingInfo(
                    Icons.Default.Security,
                    "Two-step verification",
                    "Protect your account with an additional password",
                    modifier = Modifier.clickable { accountSetting = AccountSetting.TwoStepVerification },
                )
                SettingInfo(
                    Icons.Default.Storage,
                    "Active sessions",
                    "Review devices connected to this account",
                    modifier = Modifier.clickable { accountSetting = AccountSetting.ActiveSessions },
                )
            }

            SettingsSection("Appearance") {
                SettingToggle(Icons.Default.Palette, "Dark theme", "Use Telegram's dark color palette", settings.darkTheme, viewModel::updateTheme)
                SettingInfo(
                    icon = Icons.Default.Language,
                    title = "Language",
                    supporting = settings.language,
                    modifier = Modifier.clickable { showLanguageDialog = true },
                )
            }

            SettingsSection("Notifications") {
                SettingToggle(Icons.Default.Notifications, "Notifications", "Show notifications for new messages", settings.notificationsEnabled, viewModel::updateNotifications)
                SettingToggle(Icons.Default.Info, "Message preview", "Show message text in notifications", settings.showMessagePreview, viewModel::updateMessagePreview)
                SettingToggle(Icons.Default.VolumeUp, "In-app sounds", "Play a sound for incoming messages", settings.inAppSounds, viewModel::updateInAppSounds)
                SettingToggle(Icons.Default.Vibration, "Vibration", "Vibrate for incoming messages", settings.vibration, viewModel::updateVibration)
            }

            SettingsSection("Data and Storage") {
                SettingToggle(Icons.Default.Download, "Automatic media download", "Download media when connected", settings.autoDownloadMedia, viewModel::updateAutoDownload)
                SettingToggle(Icons.Default.Photo, "Save to gallery", "Save received photos and videos to the gallery", settings.saveToGallery, viewModel::updateSaveToGallery)
                SettingToggle(Icons.Default.DataUsage, "Use less data", "Prefer smaller media and fewer background transfers", settings.useLessData, viewModel::updateUseLessData)
                SettingInfo(Icons.Default.Storage, "Storage usage", "Cached media is kept in the app's private storage")
            }

            SettingsSection("Chat Settings") {
                SettingToggle(Icons.Default.Keyboard, "Send by Enter", "Send a message when Enter is pressed", settings.sendByEnter, viewModel::updateSendByEnter)
                SettingToggle(Icons.Default.Link, "Link previews", "Generate previews for links in messages", settings.linkPreviews, viewModel::updateLinkPreviews)
                SettingToggle(Icons.Default.Chat, "Reduce animations", "Use fewer motion effects in the interface", settings.reduceAnimations, viewModel::updateReduceAnimations)
            }

            SettingsSection("About") {
                SettingInfo(Icons.Default.Info, "Chatwave", "Unofficial Telegram client · Version 0.1.0")
                Text(
                    "Telegram and TDLib are separate projects with their own licenses. Chatwave uses TDLib and does not copy Telegram's official Android UI.",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(onClick = viewModel::logout, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("Log out")
            }
        }
    }

    if (showLanguageDialog) {
        val languages = listOf("System default", "English", "简体中文")
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Language") },
            text = {
                Column {
                    languages.forEach { language ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                viewModel.updateLanguage(language)
                                showLanguageDialog = false
                            }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = settings.language == language, onClick = {
                                viewModel.updateLanguage(language)
                                showLanguageDialog = false
                            })
                            Text(language)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLanguageDialog = false }) { Text("Cancel") } },
        )
    }

    accountToRemove?.let { account ->
        AlertDialog(
            onDismissRequest = { accountToRemove = null },
            title = { Text("Remove account?") },
            text = { Text("Remove ${account.label} from Chatwave on this device? The Telegram account itself will not be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    accountManager.removeAccount(account.id)
                    accountToRemove = null
                    if (account.id == activeAccountId) onAccountChanged()
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { accountToRemove = null }) { Text("Cancel") } },
        )
    }

    accountSetting?.let { setting ->
        val title = when (setting) {
            AccountSetting.Profile -> "Profile"
            AccountSetting.PhoneNumber -> "Phone number"
            AccountSetting.TwoStepVerification -> "Two-step verification"
            AccountSetting.ActiveSessions -> "Active sessions"
        }
        val body = when (setting) {
            AccountSetting.Profile -> buildString {
                append("Name: ")
                append(currentUser?.displayName ?: activeAccount?.label ?: "Telegram account")
                currentUser?.username?.takeIf { it.isNotBlank() }?.let { append("\nUsername: @").append(it) }
            }
            AccountSetting.PhoneNumber -> currentUser?.phoneNumber?.takeIf { it.isNotBlank() }?.let { "Phone number: +$it" }
                ?: "Telegram manages this phone number."
            AccountSetting.TwoStepVerification -> "Two-step verification is managed by Telegram for this account."
            AccountSetting.ActiveSessions -> "Your Telegram sessions are managed by Telegram and TDLib."
        }
        AlertDialog(
            onDismissRequest = { accountSetting = null },
            title = { Text(title) },
            text = {
                if (setting == AccountSetting.Profile) {
                    Column {
                        OutlinedTextField(
                            value = profileFirstName,
                            onValueChange = { profileFirstName = it },
                            label = { Text("First name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = profileLastName,
                            onValueChange = { profileLastName = it },
                            label = { Text("Last name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        OutlinedTextField(
                            value = profileUsername,
                            onValueChange = { profileUsername = it.removePrefix("@").replace(" ", "") },
                            label = { Text("Username") },
                            prefix = { Text("@") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                } else {
                    Text(body)
                }
            },
            confirmButton = {
                if (setting == AccountSetting.Profile) {
                    TextButton(
                        onClick = {
                            viewModel.updateProfile(profileFirstName.trim(), profileLastName.trim(), profileUsername.trim())
                            accountSetting = null
                        },
                        enabled = profileFirstName.isNotBlank(),
                    ) { Text("Save") }
                } else {
                    TextButton(onClick = { accountSetting = null }) { Text("Done") }
                }
            },
            dismissButton = { TextButton(onClick = { accountSetting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Text(title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) { Column { content() } }
}

@Composable
private fun SettingToggle(icon: ImageVector, title: String, supporting: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(title) },
        supportingContent = { Text(supporting) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

@Composable
private fun SettingInfo(
    icon: ImageVector,
    title: String,
    supporting: String,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable (() -> Unit))? = null,
) {
    ListItem(
        modifier = modifier,
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(title) },
        supportingContent = { Text(supporting) },
        trailingContent = trailingContent,
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f), modifier = Modifier.padding(start = 72.dp))
}
