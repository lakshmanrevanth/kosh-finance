package com.neuralroots.kosh.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/*
 * All money is stored as Long in minor units (paise), so ₹1,234.50 == 123450.
 * All dates are epoch milliseconds.
 * Every synced entity carries a stable `uuid`, which is the row key in Google Sheets.
 */

fun newUuid(): String = UUID.randomUUID().toString()
fun nowMs(): Long = System.currentTimeMillis()

enum class AccountType(val label: String) {
    BANK("Bank account"),
    SAVINGS("Savings"),
    CASH("Cash"),
    CREDIT_CARD("Credit card"),
    WALLET("Wallet / UPI"),
    INVESTMENT("Investment"),
}

/**
 * Money movement types. Balances:
 *  inflow  = INCOME, LOAN_IN, TRANSFER into toAccountId
 *  outflow = EXPENSE, LOAN_OUT, TRANSFER out of accountId
 * Only EXPENSE / INCOME count towards spending/earning reports.
 */
enum class TxType(val label: String) {
    EXPENSE("Expense"),
    INCOME("Income"),
    TRANSFER("Transfer"),
    LOAN_OUT("Loan · money out"),
    LOAN_IN("Loan · money in"),
}

enum class CategoryKind { EXPENSE, INCOME }

enum class PaymentMode(val label: String) {
    UPI("UPI"),
    CARD("Card"),
    CASH("Cash"),
    NET_BANKING("Net banking"),
    AUTO_DEBIT("Auto-debit"),
    CHEQUE("Cheque"),
    OTHER("Other"),
}

enum class EmiStatus { ACTIVE, CLOSED }

enum class LoanDirection(val label: String, val short: String) {
    LENT("I lent money (they owe me)", "They owe you"),
    BORROWED("I borrowed money (I owe them)", "You owe"),
}

enum class LoanStatus { OPEN, SETTLED }

enum class LoanEntryKind(val label: String) {
    REPAYMENT("Repayment"),
    TOPUP("Added to loan"),
}

@Entity(tableName = "accounts", indices = [Index(value = ["uuid"], unique = true)])
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val name: String,
    val type: AccountType = AccountType.BANK,
    /** Balance on the day tracking started. Negative for a credit card with dues. */
    val openingBalance: Long = 0,
    val institution: String = "",
    val last4: String = "",
    val color: Int = 0xFF5B5BD6.toInt(),
    val includeInTotal: Boolean = true,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = nowMs(),
    val updatedAt: Long = nowMs(),
)

@Entity(tableName = "categories", indices = [Index(value = ["uuid"], unique = true)])
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val name: String,
    val kind: CategoryKind = CategoryKind.EXPENSE,
    val emoji: String = "🏷️",
    val color: Int = 0xFF5B5BD6.toInt(),
    /** 0 = no budget. */
    val monthlyBudget: Long = 0,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = nowMs(),
    val updatedAt: Long = nowMs(),
)

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index("date"), Index("accountId"), Index("toAccountId"), Index("categoryId"),
        Index("emiId"), Index("loanId"),
    ],
)
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val type: TxType = TxType.EXPENSE,
    val amount: Long,
    val accountId: Long,
    /** Only for TRANSFER. */
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val payee: String = "",
    val note: String = "",
    val date: Long = nowMs(),
    val paymentMode: PaymentMode = PaymentMode.UPI,
    val emiId: Long? = null,
    val loanId: Long? = null,
    val createdAt: Long = nowMs(),
    val updatedAt: Long = nowMs(),
)

@Entity(tableName = "emis", indices = [Index(value = ["uuid"], unique = true)])
data class Emi(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val name: String,
    val lender: String = "",
    val principal: Long = 0,
    /** Annual interest rate in percent, e.g. 10.5. */
    val annualRate: Double = 0.0,
    val tenureMonths: Int,
    val emiAmount: Long,
    /** Due date of installment #1. Installment n is due n-1 months later (same day, clamped). */
    val firstDueDate: Long,
    /** Account the EMI is debited from. */
    val accountId: Long? = null,
    val categoryId: Long? = null,
    /** Installments already paid before tracking in this app (no transactions created). */
    val paidBeforeTracking: Int = 0,
    /** Automatically record the payment on the due date. */
    val autoPay: Boolean = false,
    val remindDaysBefore: Int = 3,
    val note: String = "",
    val status: EmiStatus = EmiStatus.ACTIVE,
    val createdAt: Long = nowMs(),
    val updatedAt: Long = nowMs(),
)

@Entity(
    tableName = "emi_payments",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["emiId", "installmentNo"], unique = true),
        Index("transactionId"),
    ],
)
data class EmiPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val emiId: Long,
    val installmentNo: Int,
    val dueDate: Long,
    val paidDate: Long,
    val amount: Long,
    val transactionId: Long? = null,
    val note: String = "",
    val createdAt: Long = nowMs(),
)

@Entity(tableName = "people", indices = [Index(value = ["uuid"], unique = true)])
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val name: String,
    val phone: String = "",
    val note: String = "",
    val createdAt: Long = nowMs(),
    val updatedAt: Long = nowMs(),
)

@Entity(
    tableName = "loans",
    indices = [Index(value = ["uuid"], unique = true), Index("personId")],
)
data class Loan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val personId: Long,
    val direction: LoanDirection,
    val principal: Long,
    /** Simple annual interest in percent; 0 = interest free. */
    val annualRate: Double = 0.0,
    val startDate: Long = nowMs(),
    val dueDate: Long? = null,
    /** Account the money left/entered when the loan was created (optional). */
    val accountId: Long? = null,
    val transactionId: Long? = null,
    val note: String = "",
    val status: LoanStatus = LoanStatus.OPEN,
    val createdAt: Long = nowMs(),
    val updatedAt: Long = nowMs(),
)

@Entity(
    tableName = "loan_entries",
    indices = [Index(value = ["uuid"], unique = true), Index("loanId"), Index("transactionId")],
)
data class LoanEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val loanId: Long,
    val kind: LoanEntryKind = LoanEntryKind.REPAYMENT,
    val amount: Long,
    val date: Long = nowMs(),
    val accountId: Long? = null,
    val transactionId: Long? = null,
    val note: String = "",
    val createdAt: Long = nowMs(),
)

/** Pending changes to push to Google Sheets. One row per (entity, uuid). */
@Entity(tableName = "outbox", indices = [Index(value = ["entity", "uuid"], unique = true)])
data class OutboxItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entity: String,
    val uuid: String,
    /** "upsert" or "delete" */
    val op: String,
    val createdAt: Long = nowMs(),
    val attempts: Int = 0,
    val lastError: String? = null,
)

/** Pending changes to upload to the user's Firebase cloud backup. One row per (collection, uuid). */
@Entity(tableName = "cloud_outbox", indices = [Index(value = ["collection", "uuid"], unique = true)])
data class CloudOutboxItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Firestore collection name, see [com.neuralroots.kosh.cloud.CloudMapper]. */
    val collection: String,
    val uuid: String,
    val deleted: Boolean = false,
    val createdAt: Long = nowMs(),
)

object SyncEntity {
    const val TXN = "Transactions"
    const val ACCOUNT = "Accounts"
    const val CATEGORY = "Categories"
    const val EMI = "EMIs"
    const val EMI_PAYMENT = "EMI_Payments"
    const val PERSON = "People"
    const val LOAN = "Loans"
    const val LOAN_ENTRY = "Loan_Entries"
    const val OP_UPSERT = "upsert"
    const val OP_DELETE = "delete"
}

// ---------- Query projections ----------

data class AccountWithBalance(
    @Embedded val account: Account,
    val balance: Long,
)

data class TxnRow(
    @Embedded val txn: Txn,
    val categoryName: String?,
    val categoryEmoji: String?,
    val categoryColor: Int?,
    val accountName: String?,
    val toAccountName: String?,
)

data class CategoryTotal(
    val categoryId: Long,
    val name: String,
    val emoji: String,
    val color: Int,
    val budget: Long,
    val total: Long,
)

data class PeriodTotal(
    /** "yyyy-MM" or "yyyy-MM-dd" depending on query. */
    val period: String,
    val expense: Long,
    val income: Long,
)

data class LoanWithStats(
    @Embedded val loan: Loan,
    val personName: String,
    val repaid: Long,
    val topups: Long,
) {
    val totalLent: Long get() = loan.principal + topups
    val outstanding: Long get() = (loan.principal + topups - repaid).coerceAtLeast(0)
}
