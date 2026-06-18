package com.iptvapp.ui.home

import com.iptvapp.data.local.entities.ChannelEntity

sealed class FavoriteListItem {
    data class Header(
        val categoryId: String,
        val categoryName: String
    ) : FavoriteListItem()

    data class Channel(
        val channel: ChannelEntity
    ) : FavoriteListItem()
}