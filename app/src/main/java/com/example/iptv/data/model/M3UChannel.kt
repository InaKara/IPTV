package com.example.iptv.data.model

import com.google.gson.annotations.SerializedName

// Stable cache field names also survive release obfuscation. Envelope versions own migrations.
data class M3UChannel(
    @SerializedName("name") val name: String,
    @SerializedName("groupTitle") val groupTitle: String,
    @SerializedName("logoUrl") val logoUrl: String?,
    @SerializedName("streamUrl") val streamUrl: String,
    @SerializedName("userAgent") val userAgent: String?,
    @SerializedName("tvgId") val tvgId: String?,
    @SerializedName("tvgName") val tvgName: String?,
    @SerializedName("isVod") val isVod: Boolean,
    @SerializedName("providerNumber") val providerNumber: String? = null
)
