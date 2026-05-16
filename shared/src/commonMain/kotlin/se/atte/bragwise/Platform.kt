package se.atte.bragwise

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform