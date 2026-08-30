package com.zango.pokertracker.core.time

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * When a game was played, as "30 Aug 2026 · 20:15".
 *
 * The zone and locale are parameters rather than read inside, so history reads in the host's own
 * settings in the app while tests can pin both and stay deterministic.
 */
fun formatGameDate(
    epochMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", locale)
    .format(Instant.ofEpochMilli(epochMillis).atZone(zone))
