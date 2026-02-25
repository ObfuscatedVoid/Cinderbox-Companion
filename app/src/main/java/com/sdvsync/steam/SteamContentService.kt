package com.sdvsync.steam

import com.sdvsync.logging.AppLogger
import `in`.dragonbra.javasteam.steam.handlers.steamapps.PICSRequest
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.PICSProductInfoCallback
import `in`.dragonbra.javasteam.types.KeyValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class SteamContentService(
    private val clientManager: SteamClientManager,
) {
    companion object {
        private const val TAG = "SteamContent"
        const val STARDEW_APP_ID = 413150
    }

    data class BranchInfo(
        val name: String,
        val buildId: Long,
        val passwordRequired: Boolean,
        val timeUpdated: Long,
    )

    data class AppContentInfo(
        val branches: List<BranchInfo>,
    )

    suspend fun getAppBranches(): AppContentInfo = withContext(Dispatchers.IO) {
        val steamApps = clientManager.apps

        // 1. Get PICS access token for the app
        AppLogger.d(TAG, "Requesting PICS access token for AppID $STARDEW_APP_ID...")
        val tokensResult = steamApps.picsGetAccessTokens(app = STARDEW_APP_ID).await()
        val accessToken = tokensResult.appTokens[STARDEW_APP_ID] ?: 0L

        if (accessToken == 0L && tokensResult.appTokensDenied.contains(STARDEW_APP_ID)) {
            AppLogger.w(TAG, "Access token denied for AppID $STARDEW_APP_ID")
        }

        AppLogger.d(TAG, "Got access token: $accessToken")

        // 2. Get product info using callback subscription (AsyncJobMultiple)
        val deferred = CompletableDeferred<PICSProductInfoCallback>()
        val sub = clientManager.callbackMgr.subscribe(PICSProductInfoCallback::class.java) { callback ->
            if (callback.apps.containsKey(STARDEW_APP_ID) || callback.unknownApps.contains(STARDEW_APP_ID)) {
                deferred.complete(callback)
            }
        }

        steamApps.picsGetProductInfo(
            app = PICSRequest(STARDEW_APP_ID, accessToken),
        )

        val productInfo = try {
            withTimeout(15_000) { deferred.await() }
        } finally {
            sub.close()
        }

        val appInfo = productInfo.apps[STARDEW_APP_ID]
        if (appInfo == null) {
            AppLogger.w(TAG, "No product info returned for AppID $STARDEW_APP_ID")
            return@withContext AppContentInfo(branches = emptyList())
        }

        // 3. Parse branches from KeyValue tree
        // Structure: appinfo -> depots -> branches -> <branchName> -> { buildid, timeupdated, pwdrequired }
        val depots = appInfo.keyValues["depots"]
        val branchesKv = depots["branches"]

        val branches = branchesKv.children.mapNotNull { branchKv ->
            val name = branchKv.name ?: return@mapNotNull null
            val buildId = branchKv["buildid"].asLong()
            val timeUpdated = branchKv["timeupdated"].asLong()
            val pwdRequired = branchKv["pwdrequired"].asBoolean()

            AppLogger.d(TAG, "Branch '$name': buildId=$buildId, timeUpdated=$timeUpdated, pwdRequired=$pwdRequired")

            BranchInfo(
                name = name,
                buildId = buildId,
                passwordRequired = pwdRequired,
                timeUpdated = timeUpdated,
            )
        }.sortedWith(compareByDescending<BranchInfo> { it.name == "public" }.thenByDescending { it.timeUpdated })

        AppLogger.d(TAG, "Found ${branches.size} branches for AppID $STARDEW_APP_ID")
        AppContentInfo(branches = branches)
    }
}
