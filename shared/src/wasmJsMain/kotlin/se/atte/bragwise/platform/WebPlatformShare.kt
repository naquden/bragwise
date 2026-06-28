package se.atte.bragwise.platform

@JsFun("(url, title) => { if (navigator.share) { navigator.share({ title: title, url: url }); } else if (navigator.clipboard) { navigator.clipboard.writeText(url); } }")
private external fun jsShare(url: String, title: String)

class WebPlatformShare : PlatformShare {
    override fun send(url: String, title: String, subject: String) {
        jsShare(url, title)
    }
}
