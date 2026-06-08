package se.atte.bragwise.domain

sealed interface ProfileResolution {
    data class Loaded(val profile: PublicProfile) : ProfileResolution
    data object NotFound : ProfileResolution
}

fun PublicProfile?.resolve(): ProfileResolution =
    if (this == null) ProfileResolution.NotFound else ProfileResolution.Loaded(this)
