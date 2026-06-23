package se.atte.bragwise.data

enum class AppLanguage(val tag: String, val nativeName: String?) {
    System("", null),
    English("en", "English"),
    German("de", "Deutsch"),
    Spanish("es", "Español"),
    French("fr", "Français"),
    Hindi("hi", "हिन्दी"),
    Italian("it", "Italiano"),
    Portuguese("pt", "Português"),
    Russian("ru", "Русский"),
    Swedish("sv", "Svenska"),
    ChineseSimplified("zh-CN", "中文（简体）"),
}
