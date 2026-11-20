package app.yahagi.kancollebrowser.browser

import java.net.URI
import java.security.MessageDigest
import java.util.Locale

@JvmInline
value class GameResourceCacheKey(val value: String) {
    companion object {
        /**
         * [namespace] separates bytes resolved through a rewriting route from the stock
         * cache. Kanxy rewrites the body without changing the URL, so the same URL means
         * different bytes depending on whether the request went through it.
         */
        fun from(
            url: String,
            requestHeaders: Map<String, String> = emptyMap(),
            namespace: String? = null,
            ignoreQuery: Boolean = false,
        ): GameResourceCacheKey? {
            if (url.length > 2_048) return null
            if (namespace != null && (namespace.isEmpty() || namespace.length > 64)) return null
            val uri = try {
                URI(url)
            } catch (_: Exception) {
                return null
            }
            if (!GameResourceCacheRules.isOfficialStaticUri(uri)) return null
            val path = uri.rawPath ?: return null
            // A rewriting route keys by path only: the game and the mod script rewrite the
            // version query on almost every document, which would otherwise miss forever.
            val query = if (ignoreQuery) "" else uri.rawQuery?.let { "?$it" }.orEmpty()
            val scope = namespace?.let { "route=$it|" }.orEmpty()
            val base = "https://${uri.host.lowercase(Locale.ROOT)}$path$query"
            val forwarded = GameResourceCacheRules.boundedForwardedRequestHeaders(requestHeaders)
                ?: return null
            if (GameResourceCacheRules.isShareableStaticUri(uri)) {
                return GameResourceCacheKey(scope + base)
            }
            val variant = forwarded.entries
                .sortedBy { it.key.lowercase(Locale.ROOT) }
            if (variant.isEmpty()) return GameResourceCacheKey(scope + base)
            val hasher = MessageDigest.getInstance("SHA-256")
            variant.forEachIndexed { index, (name, value) ->
                if (index > 0) hasher.update('\n'.code.toByte())
                hasher.update(name.lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8))
                hasher.update(':'.code.toByte())
                hasher.update(value.toByteArray(Charsets.UTF_8))
            }
            val digest = hasher.digest()
                .joinToString("") { "%02x".format(it) }
            return GameResourceCacheKey("$scope$base|headers=$digest")
        }
    }
}

