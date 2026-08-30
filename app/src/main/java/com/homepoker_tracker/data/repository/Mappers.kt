package com.homepoker_tracker.data.repository

import com.homepoker_tracker.core.money.ChipRate
import com.homepoker_tracker.core.money.Chips
import com.homepoker_tracker.core.money.Money
import com.homepoker_tracker.data.local.entity.BuyInEntity
import com.homepoker_tracker.data.local.entity.GameEntity
import com.homepoker_tracker.data.local.entity.GamePlayerWithDetails
import com.homepoker_tracker.data.local.entity.GameSummaryRow
import com.homepoker_tracker.data.local.entity.GameWithPlayers
import com.homepoker_tracker.data.local.entity.PlayerEntity
import com.homepoker_tracker.domain.model.BuyIn
import com.homepoker_tracker.domain.model.Game
import com.homepoker_tracker.domain.model.GameSnapshot
import com.homepoker_tracker.domain.model.GameSummary
import com.homepoker_tracker.domain.model.Player
import com.homepoker_tracker.domain.model.Seat

/**
 * The boundary where raw `Long` micros become typed [Money] and [Chips]. Above this line a bare
 * number is never passed around, so a chip count cannot be mistaken for a cash amount.
 */

fun PlayerEntity.toDomain(): Player = Player(
    id = id,
    name = name,
    createdAt = createdAt,
    isArchived = isArchived,
)

fun GameEntity.toDomain(): Game = Game(
    id = id,
    name = name,
    smallBlind = Money(smallBlindMicros),
    bigBlind = Money(bigBlindMicros),
    chipRate = ChipRate(chipValueMicros),
    defaultBuyIn = Money(defaultBuyInMicros),
    payoutRounding = Money(payoutRoundingMicros),
    startedAt = startedAt,
    endedAt = endedAt,
    status = status,
)

fun BuyInEntity.toDomain(): BuyIn = BuyIn(
    id = id,
    amount = Money(amountMicros),
    createdAt = createdAt,
)

fun GamePlayerWithDetails.toDomain(): Seat = Seat(
    id = seat.id,
    player = player.toDomain(),
    joinedAt = seat.joinedAt,
    cashedOutAt = seat.cashedOutAt,
    finalChips = seat.finalChipCount?.let { Chips(it) },
    buyIns = buyIns.sortedBy { it.createdAt }.map { it.toDomain() },
)

fun GameWithPlayers.toDomain(): GameSnapshot = GameSnapshot(
    game = game.toDomain(),
    seats = seats.sortedBy { it.seat.joinedAt }.map { it.toDomain() },
)

fun GameSummaryRow.toDomain(): GameSummary = GameSummary(
    game = game.toDomain(),
    playerCount = playerCount,
    buyInCount = buyInCount,
    totalOnTable = Money(totalBuyInMicros),
)
