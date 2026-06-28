package se.atte.bragwise.data

@JsFun("(k) => localStorage.getItem(k)")
internal external fun lsGet(key: String): String?

@JsFun("(k, v) => { localStorage.setItem(k, v); }")
internal external fun lsSet(key: String, value: String)

@JsFun("(k) => { localStorage.removeItem(k); }")
internal external fun lsRemove(key: String)
