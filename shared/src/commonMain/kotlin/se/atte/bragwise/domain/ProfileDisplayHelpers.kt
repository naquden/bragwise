package se.atte.bragwise.domain

const val GENERIC_DISPLAY_NAME = "Unknown player"

fun PublicProfile?.displayNameOrGeneric(): String =
    this?.displayName?.takeIf { it.isNotBlank() } ?: GENERIC_DISPLAY_NAME

fun PublicProfile?.avatarSeedOrGeneric(): String =
    this?.avatarSeed?.takeIf { it.isNotBlank() } ?: ""
