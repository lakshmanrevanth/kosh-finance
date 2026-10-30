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
    ],
    version = 2,
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

    companion object {
        fun build(context: Context): KoshDatabase =
            Room.databaseBuilder(context, KoshDatabase::class.java, "kosh.db")
                .addMigrations(MIGRATION_1_2)
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
    }
}
