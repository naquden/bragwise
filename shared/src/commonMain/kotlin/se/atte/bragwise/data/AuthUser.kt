package se.atte.bragwise.data

data class AuthUser(
    val uid: String,
    val email: String?,
    val isAnonymous: Boolean,
)
