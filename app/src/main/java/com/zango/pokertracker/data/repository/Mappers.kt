package com.zango.pokertracker.data.repository

import com.zango.pokertracker.core.money.ChipRate
import com.zango.pokertracker.core.money.Chips
import com.zango.pokertracker.core.money.Money
import com.zango.pokertracker.data.local.entity.BuyInEntity
import com.zango.pokertracker.data.local.entity.ChipReturnEntity
import com.zango.pokertracker.data.local.entity.GameEntity
import com.zango.pokertracker.data.local.entity.GamePlayerWithDetails
import com.zango.pokertracker.data.local.entity.GameSummaryRow
import com.zango.pokertracker.data.local.entity.GameWithPlayers
import com.zango.pokertracker.data.local.entity.PlayerEntity
import com.zango.pokertracker.domain.model.BuyIn
import com.zango.pokertracker.domain.model.ChipReturn
import com.zango.pokertracker.domain.model.Game
import com.zango.pokertracker.domain.model.GameSnapshot
import com.zango.pokertracker.domain.model.GameSummary
import com.zango.pokertracker.domain.model.Player
import com.zango.pokertracker.domain.model.Seat

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

fun ChipReturnEntity.toDomain(): ChipReturn = ChipReturn(
    id = id,
    chips = Chips(chips),
    createdAt = createdAt,
)

fun GamePlayerWithDetails.toDomain(): Seat = Seat(
    id = seat.id,
    player = player.toDomain(),
    joinedAt = seat.joinedAt,
    cashedOutAt = seat.cashedOutAt,
    finalChips = seat.finalChipCount?.let { Chips(it) },
    buyIns = buyIns.sortedBy { it.createdAt }.map { it.toDomain() },
    chipReturns = chipReturns.sortedBy { it.createdAt }.map { it.toDomain() },
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
