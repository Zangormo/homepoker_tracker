package com.zango.pokertracker.data.local.converter

import com.zango.pokertracker.domain.model.GameStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Status is stored by name rather than ordinal, so that reordering the enum cannot silently
 * reinterpret rows that are already in the database.
 */
class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `every status round trips through the database`() {
        GameStatus.entries.forEach { status ->
            assertEquals(status, converters.toGameStatus(converters.fromGameStatus(status)))
        }
    }

    @Test
    fun `a status is written as its name, not its position`() {
        assertEquals("IN_PROGRESS", converters.fromGameStatus(GameStatus.IN_PROGRESS))
        assertEquals("FINISHED", converters.fromGameStatus(GameStatus.FINISHED))
    }

    /**
     * A value written by a newer version of the app, or a corrupted row, must not take the whole
     * history list down with it. Reading it as still running is the recoverable answer: the host
     * can open the game and finish it.
     */
    @Test
    fun `an unrecognised value reads as still running rather than throwing`() {
        assertEquals(GameStatus.IN_PROGRESS, converters.toGameStatus("ABANDONED"))
        assertEquals(GameStatus.IN_PROGRESS, converters.toGameStatus(""))
        assertEquals(GameStatus.IN_PROGRESS, converters.toGameStatus("in_progress"))
    }
}
