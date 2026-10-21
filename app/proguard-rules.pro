# History uses explicit JsonObject fields and schema versions, not reflective M3UChannel DTOs.
# Field names in the on-disk schema must remain literal; no broad app/model keep rule is required.
# Gson, Hilt and Media3 supply their consumer rules. Release tests verify history round-trips.
