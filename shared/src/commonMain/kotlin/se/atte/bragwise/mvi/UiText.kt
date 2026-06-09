package se.atte.bragwise.mvi

import org.jetbrains.compose.resources.StringResource

data class UiText(val res: StringResource, val args: List<Any> = emptyList())
