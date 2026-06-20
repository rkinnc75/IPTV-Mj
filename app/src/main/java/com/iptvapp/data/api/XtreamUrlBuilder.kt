package com.iptvapp.data.api

import android.net.Uri

class XtreamUrlBuilder(
    serverUrl: String,
    username: String,
    password: String
) {
    // Normalize the base once: trim whitespace, default to http:// if the user
    // omitted the scheme, and drop any trailing slash.
    private val base: String = run {
        val s = serverUrl.trim().trimEnd('/')
        if (s.startsWith("http://") || s.startsWith("https://")) s else "http://$s"
    }
    // Credentials go into stream PATH segments (not query params), so they must
    // be URL-encoded — a '/', space, '#', '?' or non-ASCII char in the password
    // would otherwise corrupt the path and break playback.
    private val user: String = Uri.encode(username)
    private val pass: String = Uri.encode(password)

    fun apiUrl(): String = "$base/player_api.php"

    fun liveStreamUrl(streamId: Int, format: String = "m3u8"): String =
        "$base/live/$user/$pass/$streamId.$format"

    fun vodStreamUrl(streamId: Int, containerExtension: String): String =
        "$base/movie/$user/$pass/$streamId.$containerExtension"

    fun seriesStreamUrl(episodeId: String, containerExtension: String): String =
        "$base/series/$user/$pass/$episodeId.$containerExtension"

    companion object {
        fun isValidServerUrl(url: String): Boolean {
            val u = url.trim()
            if (!(u.startsWith("http://") || u.startsWith("https://"))) return false
            return Uri.parse(u).host?.isNotEmpty() == true
        }
    }
}
