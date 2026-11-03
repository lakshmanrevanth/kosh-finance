package com.neuralroots.kosh.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Account::class, Category::class, Txn::class,
        Emi::class, EmiPayment::class,
        Person::class, Loan::class, LoanEntry::class,
        OutboxItem::class, CloudOutboxItem::class,
        Recurring::class, DetectedTxn::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class KoshDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun txnDao(): TxnDao
    abstract fun emiDao(): EmiDao
    abstract fun loanDao(): LoanDao
    abstract fun outboxDao(): OutboxDao
    abstract fun cloudOutboxDao(): CloudOutboxDao
    abstract fun recurringDao(): RecurringDao
    abstract fun detectedDao(): DetectedDao

    companion object {
        fun build(context: Context): KoshDatabase =
            Room.databaseBuilder(context, KoshDatabase::class.java, "kosh.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        /** v2: adds the Firebase cloud-backup outbox. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cloud_outbox` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`collection` TEXT NOT NULL, `uuid` TEXT NOT NULL, `deleted` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_cloud_outbox_collection_uuid` ON `cloud_outbox` (`collection`, `uuid`)")
            }
        }

        /** v3: recurring bills/income and SMS-detected transactions. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recurring` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, `type` TEXT NOT NULL, `amount` INTEGER NOT NULL, `accountId` INTEGER NOT NULL, " +
                        "`toAccountId` INTEGER, `categoryId` INTEGER, `payee` TEXT NOT NULL, `note` TEXT NOT NULL, " +
                        "`paymentMode` TEXT NOT NULL, `frequency` TEXT NOT NULL, `nextDate` INTEGER NOT NULL, `anchorDay` INTEGER NOT NULL, " +
                        "`endDate` INTEGER, `autoAdd` INTEGER NOT NULL, `remindDaysBefore` INTEGER NOT NULL, `active` INTEGER NOT NULL, " +
                        "`lastPostedDate` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_uuid` ON `recurring` (`uuid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_nextDate` ON `recurring` (`nextDate`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `detected` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fingerprint` TEXT NOT NULL, " +
                        "`sender` TEXT NOT NULL, `body` TEXT NOT NULL, `amount` INTEGER NOT NULL, `isDebit` INTEGER NOT NULL, " +
                        "`merchant` TEXT NOT NULL, `accountLast4` TEXT NOT NULL, `refNo` TEXT NOT NULL, `mode` TEXT NOT NULL, " +
                        "`date` INTEGER NOT NULL, `status` TEXT NOT NULL, `txnId` INTEGER, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_detected_fingerprint` ON `detected` (`fingerprint`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_detected_status` ON `detected` (`status`)")
            }
        }
    }
}
