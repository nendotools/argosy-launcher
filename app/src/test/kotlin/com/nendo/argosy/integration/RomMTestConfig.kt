package com.nendo.argosy.integration

import java.io.File
import java.util.Properties

object RomMTestConfig {
    private val properties: Properties? by lazy {
        val locations = listOf(
            File("romm-test.properties"),
            File("../romm-test.properties"),
            File(System.getProperty("user.dir"), "romm-test.properties"),
            File(System.getProperty("user.dir"), "../romm-test.properties")
        )
        val file = locations.firstOrNull { it.exists() }
        file?.let { Properties().apply { load(it.inputStream()) } }
    }

    val isAvailable: Boolean
        get() = properties != null &&
            url.isNotBlank() &&
            username.isNotBlank() &&
            password.isNotBlank()

    val url: String
        get() = properties?.getProperty("ROMM_URL")?.let {
            if (it.endsWith("/")) it else "$it/"
        } ?: ""

    val username: String
        get() = properties?.getProperty("ROMM_USERNAME") ?: ""

    val password: String
        get() = properties?.getProperty("ROMM_PASSWORD") ?: ""
}
