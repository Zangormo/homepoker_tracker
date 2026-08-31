package com.zango.pokertracker.data.local.entity

/**
 * One stake level a game has been played at, with the last time it was.
 *
 * Games already record their blinds, so the stakes the host actually uses need no table of their
 * own: they are whatever has been played, read straight back out.
 */
data class StakesRow(
    val smallBlindMicros: Long,
    val bigBlindMicros: Long,
    val lastPlayedAt: Long,
)
