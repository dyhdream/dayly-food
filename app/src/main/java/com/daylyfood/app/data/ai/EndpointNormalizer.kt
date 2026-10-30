package com.daylyfood.app.data.ai

/**
 * Users paste whatever the provider docs show: sometimes the full endpoint, sometimes just
 * the host, sometimes without a scheme. Pasting the host and getting 404 is the single most
 * common setup mistake, so we finish the URL instead of refusing it.
 */
object EndpointNormalizer {

    private const val SUFFIX = "/chat/completions"

    fun normalize(raw: String): String {
        var url = raw.trim().replace(Regex("[\\s\\u3000]+"), "")
        if (url.isEmpty()) return ""
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            url = "https://$url"
        }
        url = url.trimEnd('/')

        // Already points at a completion endpoint (any casing): keep it, but normalise the
        // scheme, since plain http redirects and the redirect is not followed on POST.
        if (url.contains(SUFFIX, ignoreCase = true)) {
            val pathIndex = url.indexOf("//") + 2
            val rest = url.substring(pathIndex)
            val slash = rest.indexOf('/')
            val host = if (slash >= 0) rest.substring(0, slash) else rest
            val path = if (slash >= 0) rest.substring(slash) else ""
            return "https://$host$path"
        }
        return url + SUFFIX
    }

    /** Shown back to the user so there is no guessing about what was actually called. */
    fun display(url: String): String = url.substringAfter("//", url)
}
