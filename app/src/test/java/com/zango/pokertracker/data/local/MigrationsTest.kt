package com.zango.pokertracker.data.local

import androidx.room.migration.Migration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The migrations, checked as a set rather than one at a time.
 *
 * This database has no destructive fallback — a host's game history is not disposable — so a
 * version bump without a matching migration is not a warning at build time, it is a crash on
 * every device that already has the app. Since that failure only shows up on a real upgrade, the
 * chain is asserted here where it costs nothing to check.
 *
 * What the migrations actually do to a database is a separate question, and one a JVM test cannot
 * answer: it needs a device. See the note on `MIGRATION_1_2` and friends.
 */
class MigrationsTest {

    /** Every `Migration` declared on the companion, found by reflection so none can be missed. */
    private val migrations: List<Migration> =
        PokerDatabase.Companion::class.java.declaredMethods
            .filter { Migration::class.java.isAssignableFrom(it.returnType) }
            .sortedBy { it.name }
            .map { it.invoke(PokerDatabase.Companion) as Migration }

    private val declaredVersion: Int = POKER_DATABASE_VERSION

    @Test
    fun `there is a migration for every version this database has ever had`() {
        assertEquals(
            "the database is at v$declaredVersion, so ${declaredVersion - 1} migrations are needed",
            declaredVersion - 1,
            migrations.size,
        )
    }

    @Test
    fun `the migrations form an unbroken chain from the first version to the current one`() {
        val chain = migrations.sortedBy { it.startVersion }

        chain.forEachIndexed { index, migration ->
            assertEquals(
                "migration ${migration.startVersion} to ${migration.endVersion} is out of place",
                index + 1,
                migration.startVersion,
            )
            assertEquals(
                "migration from ${migration.startVersion} skips a version",
                migration.startVersion + 1,
                migration.endVersion,
            )
        }
        assertEquals(declaredVersion, chain.last().endVersion)
    }

    @Test
    fun `no two migrations claim the same starting version`() {
        val starts = migrations.map { it.startVersion }

        assertEquals(starts.distinct().size, starts.size)
    }

    @Test
    fun `every migration moves the database forward`() {
        migrations.forEach { migration ->
            assertTrue(
                "migration ${migration.startVersion} to ${migration.endVersion} goes backwards",
                migration.endVersion > migration.startVersion,
            )
        }
    }
}
