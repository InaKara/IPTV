package com.example.iptv.data.model

data class M3UChannel(
    val name: String,
    val groupTitle: String,
    val logoUrl: String?,
    val streamUrl: String,
    val userAgent: String?,
    val tvgId: String?,
    val tvgName: String?,
    val isVod: Boolean
)
