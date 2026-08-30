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
import com.zango.pokertracker.data.local.entity.BuyInEntity
import com.zango.pokertracker.data.local.entity.ChipReturnEntity
import com.zango.pokertracker.data.local.entity.GameEntity
import com.zango.pokertracker.data.local.entity.GamePlayerEntity
import com.zango.pokertracker.data.local.entity.PlayerEntity

@Database(
    entities = [
        PlayerEntity::class,
        GameEntity::class,
        GamePlayerEntity::class,
        BuyInEntity::class,
        ChipReturnEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PokerDatabase : RoomDatabase() {
    abstract fun playerDao(): PlayerDao
    abstract fun gameDao(): GameDao
    abstract fun gamePlayerDao(): GamePlayerDao
    abstract fun buyInDao(): BuyInDao
    abstract fun chipReturnDao(): ChipReturnDao

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
    }
}
