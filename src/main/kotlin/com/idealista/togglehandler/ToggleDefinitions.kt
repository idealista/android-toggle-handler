package com.idealista.togglehandler

object ToggleDefinitions {

    data class ToggleData(
        val name: String,
        val jiraTask: String = "",
        val description: String = "",
        val isRemotelyConfigurable: Boolean = false,
        val activationDate: String = "",
        val activationVersion: String = "",
        val deprecationDate: String = "",
    )

    data object ToggleFile
    data object ServiceExtensionsFile
    data object RemoteSettingsDefaultsFile

    val TARGET_FILES = mapOf(
        "Toggle.kt" to ToggleFile,
        "ServiceExtensions.kt" to ServiceExtensionsFile,
        "RemoteSettingsDefaults.kt" to RemoteSettingsDefaultsFile
    )
}
