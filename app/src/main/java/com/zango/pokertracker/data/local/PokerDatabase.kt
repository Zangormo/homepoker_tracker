package com.zango.pokertracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.zango.pokertracker.data.local.converter.Converters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zango.pokertracker.data.local.dao.BuyInDao
import com.zango.pokertracker.data.local.dao.ChipReturnDao
import com.zango.pokertracker.data.local.dao.GameDao
import com.zango.pokertracker.data.local.dao.GamePlayerDao
import com.zango.pokertracker.data.local.dao.PlayerDao
import com.zango.pokertracker.data.local.dao.SettlementPaymentDao
import com.zango.pokertracker.data.local.dao.StakePresetDao
import com.zango.pokertracker.data.local.entity.BuyInEntity
import com.zango.pokertracker.data.local.entity.ChipReturnEntity
import com.zango.pokertracker.data.local.entity.GameEntity
import com.zango.pokertracker.data.local.entity.GamePlayerEntity
import com.zango.pokertracker.data.local.entity.PlayerEntity
import com.zango.pokertracker.data.local.entity.SettlementPaymentEntity
import com.zango.pokertracker.data.local.entity.StakePresetEntity
import com.zango.pokertracker.domain.model.Stakes

/**
 * The schema version. Bumped with every change to a table, and the migrations on
 * [PokerDatabase] must chain unbroken up to it: this database has no destructive fallback, so a
 * bump without a matching migration crashes every device that already has the app.
 */
const val POKER_DATABASE_VERSION: Int = 4

@Database(
    entities = [
        PlayerEntity::class,
        GameEntity::class,
        GamePlayerEntity::class,
        BuyInEntity::class,
        ChipReturnEntity::class,
        SettlementPaymentEntity::class,
        StakePresetEntity::class,
    ],
    version = POKER_DATABASE_VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PokerDatabase : RoomDatabase() {
    abstract fun playerDao(): PlayerDao
    abstract fun gameDao(): GameDao
    abstract fun gamePlayerDao(): GamePlayerDao
    abstract fun buyInDao(): BuyInDao
    abstract fun chipReturnDao(): ChipReturnDao
    abstract fun settlementPaymentDao(): SettlementPaymentDao
    abstract fun stakePresetDao(): StakePresetDao

    companion object {
        const val NAME = "poker.db"

        /**
         * Adds the mid-game chip returns table. Written out rather than falling back to a
         * destructive migration, because a host's game history is not disposable.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chip_returns` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `gamePlayerId` INTEGER NOT NULL,
                        `chips` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`gamePlayerId`) REFERENCES `game_players`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chip_returns_gamePlayerId` " +
                        "ON `chip_returns` (`gamePlayerId`)",
                )
            }
        }

        /**
         * Adds the ticks against settlement payments, and the flag on the game that says every
         * one of them has been made. Games settled before this version start unticked, which is
         * the honest answer: nobody has said whether that money changed hands.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `settlement_payments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `gameId` INTEGER NOT NULL,
                        `fromPlayerId` INTEGER NOT NULL,
                        `toPlayerId` INTEGER NOT NULL,
                        `amountMicros` INTEGER NOT NULL,
                        `markedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`gameId`) REFERENCES `games`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_settlement_payments_gameId_fromPlayerId_toPlayerId` " +
                        "ON `settlement_payments` (`gameId`, `fromPlayerId`, `toPlayerId`)",
                )
                db.execSQL(
                    "ALTER TABLE `games` ADD COLUMN `isFullyPaid` INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /**
         * Turns the stake picker's list into something the host owns.
         *
         * Until now it was read back out of the games that used it, which meant a one-off night
         * at odd blinds sat in the picker forever and a level could not be added without playing
         * it. The list is seeded with the standard ladder plus whatever has actually been played,
         * newest first, so nothing on offer before the upgrade disappears at it.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `stake_presets` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `smallBlindMicros` INTEGER NOT NULL,
                        `bigBlindMicros` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_stake_presets_smallBlindMicros_bigBlindMicros` " +
                        "ON `stake_presets` (`smallBlindMicros`, `bigBlindMicros`)",
                )
                seedStandardStakes(db)
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO stake_presets
                        (smallBlindMicros, bigBlindMicros, createdAt)
                    SELECT g.smallBlindMicros, g.bigBlindMicros, MAX(g.startedAt)
                    FROM games g
                    WHERE NOT EXISTS (
                        SELECT 1 FROM stake_presets p
                        WHERE p.smallBlindMicros = g.smallBlindMicros
                            AND p.bigBlindMicros = g.bigBlindMicros
                    )
                    GROUP BY g.smallBlindMicros, g.bigBlindMicros
                    ORDER BY MAX(g.startedAt) DESC
                    LIMIT ${Stakes.MAX_PRESETS - Stakes.COMMON.size}
                    """.trimIndent(),
                )
            }
        }

        /**
         * Lays down the standard ladder. Run both when the database is created and when an older
         * one is upgraded, because neither path covers the other.
         */
        fun seedStandardStakes(db: SupportSQLiteDatabase) {
            Stakes.COMMON.forEach { stakes ->
                db.execSQL(
                    "INSERT OR IGNORE INTO stake_presets " +
                        "(smallBlindMicros, bigBlindMicros, createdAt) VALUES (?, ?, 0)",
                    arrayOf<Any>(stakes.smallBlind.micros, stakes.bigBlind.micros),
                )
            }
        }
    }
}
