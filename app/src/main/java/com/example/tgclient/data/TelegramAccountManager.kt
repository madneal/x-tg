package com.example.tgclient.data

import android.content.Context
import com.example.tgclient.model.AccountSummary
import com.example.tgclient.security.DatabaseKeyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/** Owns one isolated TDLib repository per signed-in account. */
class TelegramAccountManager(context: Context, private val scope: CoroutineScope) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val repositories = linkedMapOf<String, TelegramRepository>()
    private val accountIds = loadAccountIds().toMutableList()
    private val accountLabels = accountIds.associateWith { id -> preferences.getString("label.$id", null) }.toMutableMap()
    private val _accounts = MutableStateFlow(accountIds.mapIndexed(::accountSummary))
    private val _activeAccountId = MutableStateFlow(
        preferences.getString(ACTIVE_ACCOUNT, DEFAULT_ACCOUNT_ID)?.takeIf { it in accountIds } ?: DEFAULT_ACCOUNT_ID,
    )

    val accounts: StateFlow<List<AccountSummary>> = _accounts.asStateFlow()
    val activeAccountId: StateFlow<String> = _activeAccountId.asStateFlow()

    init {
        repository(_activeAccountId.value)
    }

    @Synchronized
    fun repository(accountId: String): TelegramRepository {
        repositories[accountId]?.let { return it }
        val repository = TelegramRepository(appContext, scope, accountId)
        repositories[accountId] = repository
        repository.currentUser.onEach { user ->
            if (user != null) updateAccountLabel(accountId, user)
        }.launchIn(scope)
        return repository
    }

    @Synchronized
    fun switchAccount(accountId: String) {
        if (accountId !in accountIds) return
        repository(accountId)
        _activeAccountId.value = accountId
        preferences.edit().putString(ACTIVE_ACCOUNT, accountId).apply()
    }

    @Synchronized
    fun createAccount(): String {
        val id = "account_" + UUID.randomUUID().toString().replace("-", "").take(12)
        accountIds += id
        persistAccountIds()
        _accounts.value = accountIds.mapIndexed(::accountSummary)
        repository(id)
        switchAccount(id)
        return id
    }

    /** Removes an account from this device; it never deletes the Telegram account itself. */
    @Synchronized
    fun removeAccount(accountId: String): Boolean {
        if (accountId !in accountIds || accountIds.size <= 1) return false
        val wasActive = _activeAccountId.value == accountId
        val repository = repositories.remove(accountId)
        accountIds.remove(accountId)
        accountLabels.remove(accountId)
        preferences.edit().remove("label.$accountId").apply()
        persistAccountIds()
        _accounts.value = accountIds.mapIndexed(::accountSummary)

        if (wasActive) {
            val replacement = accountIds.first()
            repository(replacement)
            _activeAccountId.value = replacement
            preferences.edit().putString(ACTIVE_ACCOUNT, replacement).apply()
        }

        scope.launch(Dispatchers.IO) {
            repository?.removeFromDevice()
            clearAccountStorage(accountId)
        }
        return true
    }

    private fun clearAccountStorage(accountId: String) {
        DatabaseKeyStore(appContext, accountId).clear()
        val accountRoot = if (accountId == DEFAULT_ACCOUNT_ID) {
            File(appContext.noBackupFilesDir, "tdlib")
        } else {
            File(appContext.noBackupFilesDir, "tdlib/accounts/$accountId")
        }
        accountRoot.deleteRecursively()
    }

    private fun accountSummary(index: Int, id: String): AccountSummary =
        AccountSummary(id = id, label = accountLabels[id] ?: if (id == DEFAULT_ACCOUNT_ID) "Account 1" else "Account ${index + 1}")

    @Synchronized
    private fun updateAccountLabel(accountId: String, user: com.example.tgclient.model.TelegramUser) {
        val label = user.username?.takeIf { it.isNotBlank() }?.let { "@$it" }
            ?: user.displayName.takeIf { it.isNotBlank() && it != "User" }
            ?: return
        if (accountLabels[accountId] == label) return
        accountLabels[accountId] = label
        preferences.edit().putString("label.$accountId", label).apply()
        _accounts.value = accountIds.mapIndexed(::accountSummary)
    }

    private fun loadAccountIds(): List<String> = preferences.getString(ACCOUNT_IDS, null)
        ?.split(',')
        ?.map(String::trim)
        ?.filter(String::isNotBlank)
        ?.distinct()
        ?.ifEmpty { listOf(DEFAULT_ACCOUNT_ID) }
        ?: listOf(DEFAULT_ACCOUNT_ID)

    private fun persistAccountIds() {
        preferences.edit().putString(ACCOUNT_IDS, accountIds.joinToString(",")).apply()
    }

    private companion object {
        const val DEFAULT_ACCOUNT_ID = "default"
        const val PREFERENCES = "telegram_accounts"
        const val ACCOUNT_IDS = "account_ids"
        const val ACTIVE_ACCOUNT = "active_account"
    }
}
