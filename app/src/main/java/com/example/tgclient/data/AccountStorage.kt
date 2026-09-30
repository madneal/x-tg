package com.example.tgclient.data

import java.io.File

/** The legacy default account shares its parent directory with other accounts. */
internal fun deleteAccountDirectory(tdlibRoot: File, accountId: String) {
    if (accountId == "default") {
        tdlibRoot.listFiles()?.filter { it.name != "accounts" }?.forEach { it.deleteRecursively() }
    } else {
        File(tdlibRoot, "accounts/$accountId").deleteRecursively()
    }
}
