package com.iptvapp.data.api

class XtreamUrlBuilder(
    private val serverUrl: String,
    private val username: String,
    private val password: String
) {
    fun apiUrl(): String = "${serverUrl.trimEnd('/')}/player_api.php"

    fun liveStreamUrl(streamId: Int, format: String = "m3u8"): String =
        "${serverUrl.trimEnd('/')}/live/$username/$password/$streamId.$format"

    fun vodStreamUrl(streamId: Int, containerExtension: String): String =
        "${serverUrl.trimEnd('/')}/movie/$username/$password/$streamId.$containerExtension"

    fun seriesStreamUrl(episodeId: String, containerExtension: String): String =
        "${serverUrl.trimEnd('/')}/series/$username/$password/$episodeId.$containerExtension"

    companion object {
        fun isValidServerUrl(url: String): Boolean =
            url.startsWith("http://") || url.startsWith("https://")
    }
}
