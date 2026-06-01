package se.atte.bragwise.ui

import androidx.compose.ui.Modifier

/**
 * Exposes Compose test tags as Android resource-ids so uiautomator / ADB can
 * target them. No-op on platforms without that bridge (iOS). Android-only API
 * `testTagsAsResourceId` lives in `androidMain`; common code calls this.
 */
expect fun Modifier.enableTestTagsAsResourceId(): Modifier
