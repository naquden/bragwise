package se.atte.bragwise.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

actual fun Modifier.enableTestTagsAsResourceId(): Modifier =
    semantics { testTagsAsResourceId = true }
