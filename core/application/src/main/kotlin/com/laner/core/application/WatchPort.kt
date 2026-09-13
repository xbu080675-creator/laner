package com.laner.core.application

/**
 * Viewing-region metadata only. It is never a business-module selector.
 */
enum class WatchRegion {
    MAINLAND,
    GLOBAL,
}

data class WatchDestination(
    val id: String,
    val displayName: String,
    val region: WatchRegion,
    /** Product capability metadata only; launch details remain in the platform Adapter. */
    val nativeAppSupported: Boolean = false,
) {
    init {
        require(id.isNotBlank())
        require(displayName.isNotBlank())
    }
}

enum class WatchLaunchStatus {
    OPENED,
    /** Permission returned and the legacy delayed handoff has been scheduled, not yet proven open. */
    RESUMING,
    PERMISSION_REQUIRED,
    UNSUPPORTED,
}

data class WatchLaunchResult(
    val status: WatchLaunchStatus,
    val destinationId: String? = null,
    val message: String,
)

/**
 * Platform-neutral boundary for opening a viewing surface.
 *
 * Watch is intentionally independent from schedule/LIVE/POST source arbitration: opening a video
 * app must never change which Provider owns esports facts.
 */
interface WatchPort {
    fun destinations(): List<WatchDestination>

    fun isInstalled(destinationId: String): Boolean

    fun launch(destinationId: String): WatchLaunchResult

    fun resumePendingIfReady(): WatchLaunchResult? = null
}
