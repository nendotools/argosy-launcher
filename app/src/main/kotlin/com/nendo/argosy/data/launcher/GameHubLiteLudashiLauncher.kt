package com.nendo.argosy.data.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent

object GameHubLiteLudashiLauncher : SteamLauncher {
    override val packageName = "com.ludashi.benchmark"
    override val displayName = "GameHub Lite (Ludashi)"
    override val supportsScanning = true
    override val scanMayIncludeUninstalled = true

    override fun createLaunchIntent(steamAppId: Long): Intent = Intent().apply {
        component = ComponentName(
            packageName,
            "com.xj.landscape.launcher.ui.gamedetail.GameDetailActivity"
        )
        action = "gamehub.lite.LAUNCH_GAME"
        putExtra("steamAppId", steamAppId.toString())
        putExtra("autoStartGame", true)
    }

    override suspend fun scan(context: Context): List<ScannedSteamGame> =
        GameHubLogScanner.scan(packageName)
}
