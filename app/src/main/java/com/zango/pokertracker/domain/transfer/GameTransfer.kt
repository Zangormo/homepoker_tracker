package com.zango.pokertracker.domain.transfer

import com.zango.pokertracker.domain.model.GameSnapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * A whole game as it travels from one host's phone to another's: in a QR code, or in a file sent
 * through a messenger. There is no server in between, so this is the entire handover.
 *
 * Players travel by name, not by id. Ids are local to each phone's database; the name is what both
 * hosts know the person by, and it is what the receiving phone matches against its own roster.
 *
 * Every time is stored relative to the game's start. Absolute timestamps are thirteen digits of
 * noise that compress badly, and the QR code has little room to spare.
 */
@Serializable
data class GameTransfer(
    /** Always written, so an older app can tell a newer file apart and refuse it cleanly. */
    @SerialName("v") val version: Int,
    val name: String,
    val smallBlindMicros: Long,
    val bigBlindMicros: Long,
    val chipValueMicros: Long,
    val defaultBuyInMicros: Long,
    val payoutRoundingMicros: Long,
    val startedAt: Long,
    /** Millis after [startedAt] the game ended, or null while it is running. */
    val endedAfter: Long? = null,
    val bombPotIntervalMinutes: Int? = null,
    val isFiretruckGame: Boolean = false,
    val isRandomSeating: Boolean = false,
    val seats: List<SeatTransfer>,
) {
    val isRunning: Boolean get() = endedAfter == null

    val buyInCount: Int get() = seats.sumOf { it.buyIns.size }

    /** Cash still on the table: everything bought in, less what the bank has bought back. */
    val totalOnTableMicros: Long
        get() = seats.sumOf { seat -> seat.buyIns.sumOf { it.amount } } -
            seats.sumOf { seat -> seat.returns.sumOf { it.amount } } * chipValueMicros

    companion object {
        /** Bumped whenever a change would make an older app misread the data. */
        const val FORMAT_VERSION: Int = 1
    }
}

@Serializable
data class SeatTransfer(
    val player: String,
    val joinedAfter: Long,
    val cashedOutAfter: Long? = null,
    val finalChips: Long? = null,
    val tablePosition: Int? = null,
    /** Each buy-in in micros. */
    val buyIns: List<Entry>,
    /** Each chip return in chips. */
    val returns: List<Entry> = emptyList(),
)

/** One buy-in or chip return: how much, and how long after the start. */
@Serializable
data class Entry(
    @SerialName("a") val amount: Long,
    @SerialName("t") val after: Long,
)

fun GameSnapshot.toTransfer(): GameTransfer {
    val start = game.startedAt
    return GameTransfer(
        version = GameTransfer.FORMAT_VERSION,
        name = game.name,
        smallBlindMicros = game.smallBlind.micros,
        bigBlindMicros = game.bigBlind.micros,
        chipValueMicros = game.chipRate.chipValueMicros,
        defaultBuyInMicros = game.defaultBuyIn.micros,
        payoutRoundingMicros = game.payoutRounding.micros,
        startedAt = start,
        endedAfter = game.endedAt?.minus(start),
        bombPotIntervalMinutes = game.bombPotIntervalMinutes,
        isFiretruckGame = game.isFiretruckGame,
        isRandomSeating = game.isRandomSeating,
        seats = seats.map { seat ->
            SeatTransfer(
                player = seat.player.name,
                joinedAfter = seat.joinedAt - start,
                cashedOutAfter = seat.cashedOutAt?.minus(start),
                finalChips = seat.finalChips?.count,
                tablePosition = seat.tablePosition,
                buyIns = seat.buyIns.map { Entry(it.amount.micros, it.createdAt - start) },
                returns = seat.chipReturns.map { Entry(it.chips.count, it.createdAt - start) },
            )
        },
    )
}

/** What came out of reading something that was scanned or opened. */
sealed interface DecodedTransfer {
    data class Game(val transfer: GameTransfer) : DecodedTransfer

    /** A QR code or file that is not one of ours, or one that got damaged on the way. */
    data object NotAGame : DecodedTransfer

    /** Written by a newer version of the app, in a format this one does not know. */
    data object TooNew : DecodedTransfer
}

/**
 * Turns a game into text and back.
 *
 * JSON, deflated, then base64 so it survives anything that carries text: a QR code, a messenger,
 * a file. The prefix marks it as ours, so a stray QR code is recognised as not a game at once
 * instead of failing somewhere in the middle of parsing.
 */
object GameTransferCodec {

    private const val PREFIX = "PKTG1:"

    /** The longest text one QR code can carry at the error correction we draw it with. */
    const val MAX_QR_LENGTH: Int = 2_300

    /** Anything bigger than this is not a home game; refusing it early keeps a bad file cheap. */
    private const val MAX_INFLATED_BYTES = 1_000_000

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun encode(transfer: GameTransfer): String {
        val bytes = json.encodeToString(GameTransfer.serializer(), transfer).toByteArray()
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(deflate(bytes))
    }

    fun decode(text: String): DecodedTransfer {
        val body = text.trim().takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)
            ?: return DecodedTransfer.NotAGame
        return try {
            val raw = inflate(Base64.getUrlDecoder().decode(body))
                ?: return DecodedTransfer.NotAGame
            val transfer = json.decodeFromString(GameTransfer.serializer(), raw.decodeToString())
            when {
                transfer.version > GameTransfer.FORMAT_VERSION -> DecodedTransfer.TooNew
                !transfer.isSane() -> DecodedTransfer.NotAGame
                else -> DecodedTransfer.Game(transfer)
            }
        } catch (_: IllegalArgumentException) {
            DecodedTransfer.NotAGame
        } catch (_: SerializationException) {
            DecodedTransfer.NotAGame
        } catch (_: DataFormatException) {
            DecodedTransfer.NotAGame
        }
    }

    /**
     * The checks a file from outside has to pass before it goes anywhere near the database. The
     * repository checks again on the way in; this only lets the preview say "not a game" early.
     */
    private fun GameTransfer.isSane(): Boolean =
        name.isNotBlank() && smallBlindMicros > 0 && bigBlindMicros > smallBlindMicros &&
            chipValueMicros > 0 && defaultBuyInMicros > 0 && payoutRoundingMicros > 0 &&
            seats.isNotEmpty() &&
            seats.all { seat -> seat.player.isNotBlank() && seat.buyIns.all { it.amount > 0 } } &&
            seats.map { it.player.trim().lowercase() }.distinct().size == seats.size

    private fun deflate(bytes: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(bytes)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1_024)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        return out.toByteArray()
    }

    /** Null when the data is truncated or would inflate past [MAX_INFLATED_BYTES]. */
    private fun inflate(bytes: ByteArray): ByteArray? {
        val inflater = Inflater()
        inflater.setInput(bytes)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1_024)
        try {
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) return null
                out.write(buffer, 0, count)
                if (out.size() > MAX_INFLATED_BYTES) return null
            }
        } finally {
            inflater.end()
        }
        return out.toByteArray()
    }
}
