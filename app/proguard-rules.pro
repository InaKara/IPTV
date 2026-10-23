# History uses explicit JsonObject fields. Playlist snapshots use Gson's reflective reads:
# keep these fields from optimization (including folding the always-written schema version).
# SerializedName makes Java field obfuscation safe; no broad application keep rule is needed.
-keepclassmembers,allowobfuscation class com.example.iptv.data.storage.FileChannelSnapshots$Envelope { <fields>; }
-keepclassmembers,allowobfuscation class com.example.iptv.data.model.M3UChannel { <fields>; }
# Hilt, Gson and Media3 also supply consumer rules.
