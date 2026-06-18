package com.iptvapp.ui.guide

import com.iptvapp.data.local.entities.ChannelEntity
import com.iptvapp.data.local.entities.EpgEntity

data class GuideRow(
    val channel: ChannelEntity,
    val programs: List<EpgEntity>
)