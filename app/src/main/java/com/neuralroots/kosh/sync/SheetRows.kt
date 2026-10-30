package com.neuralroots.kosh.sync

import com.neuralroots.kosh.data.db.Account
import com.neuralroots.kosh.data.db.AccountWithBalance
import com.neuralroots.kosh.data.db.Category
import com.neuralroots.kosh.data.db.Emi
import com.neuralroots.kosh.data.db.EmiPayment
import com.neuralroots.kosh.data.db.LoanDirection
import com.neuralroots.kosh.data.db.LoanEntry
import com.neuralroots.kosh.data.db.LoanStatus
import com.neuralroots.kosh.data.db.LoanWithStats
import com.neuralroots.kosh.data.db.Person
import com.neuralroots.kosh.data.db.SyncEntity
import com.neuralroots.kosh.data.db.Txn
import com.neuralroots.kosh.data.repo.FinanceRepository
import com.neuralroots.kosh.util.Dates
import com.neuralroots.kosh.util.EmiMath
import com.neuralroots.kosh.util.Money
import kotlinx.coroutines.flow.first

typealias SheetRow = LinkedHashMap<String, Any?>

/** Column layout of every sheet (used by Sheets sync and the Excel export). */
object SheetHeaders {
    val headers: Map<String, List<String>> = mapOf(
        SyncEntity.TXN to listOf(
            "ID", "Date", "Month", "Type", "Amount", "Category", "Account", "To Account",
            "Payee", "Note", "Payment Mode", "EMI", "Loan", "Updated",
        ),
        SyncEntity.ACCOUNT to listOf(
            "ID", "Name", "Type", "Institution", "Last 4", "Opening Balance", "Current Balance", "In Net Worth", "Archived",
        ),
        SyncEntity.CATEGORY to listOf("ID", "Name", "Kind", "Emoji", "Monthly Budget", "Archived"),
        SyncEntity.EMI to listOf(
            "ID", "Name", "Lender", "Principal", "Rate %", "Tenure", "EMI Amount", "First Due", "Next Due",
            "Paid", "Remaining", "Amount Paid", "Amount Remaining", "Principal Outstanding", "Status", "Account", "Auto Pay",
        ),
        SyncEntity.EMI_PAYMENT to listOf("ID", "EMI", "Installment", "Due Date", "Paid Date", "Amount", "Note"),
        SyncEntity.PERSON to listOf("ID", "Name", "Phone", "Net"),
        SyncEntity.LOAN to listOf(
            "ID", "Person", "Direction", "Principal", "Top-ups", "Repaid", "Outstanding", "Rate %",
            "Start", "Due", "Status", "Account", "Note",
        ),
        SyncEntity.LOAN_ENTRY to listOf("ID", "Person", "Loan Direction", "Kind", "Amount", "Date", "Account", "Note"),
    )

    /** Display order (export + upload order). */
    val order = listOf(
        SyncEntity.TXN, SyncEntity.ACCOUNT, SyncEntity.CATEGORY, SyncEntity.EMI,
        SyncEntity.EMI_PAYMENT, SyncEntity.PERSON, SyncEntity.LOAN, SyncEntity.LOAN_ENTRY,
    )

    fun title(entity: String): String = when (entity) {
        SyncEntity.TXN -> "Transactions"
        SyncEntity.ACCOUNT -> "Accounts & balances"
        SyncEntity.CATEGORY -> "Categories"
        SyncEntity.EMI -> "EMIs"
        SyncEntity.EMI_PAYMENT -> "EMI payments"
        SyncEntity.PERSON -> "People"
        SyncEntity.LOAN -> "Loans"
        SyncEntity.LOAN_ENTRY -> "Loan repayments"
        else -> entity
    }
}

private fun yesNo(b: Boolean) = if (b) "Yes" else "No"
private fun money(minor: Long): Double = Money.toMajor(minor)
private fun direction(d: LoanDirection) = if (d == LoanDirection.LENT) "Lent (they owe you)" else "Borrowed (you owe)"

/**
 * Builds human-readable rows. Lookup tables are loaded lazily once per builder,
 * so create a new builder for each sync batch / export.
 */
class SheetRowBuilder(private val repo: FinanceRepository) {
    private var accountsCache: Map<Long, AccountWithBalance>? = null
    private var categoriesCache: Map<Long, Category>? = null
    private var emisCache: Map<Long, Emi>? = null
    private var paymentsCache: Map<Long, List<EmiPayment>>? = null
    private var peopleCache: Map<Long, Person>? = null
    private var loansCache: Map<Long, LoanWithStats>? = null

    private suspend fun accounts() = accountsCache
        ?: repo.accountDao.observeAllWithBalance().first().associateBy { it.account.id }.also { accountsCache = it }
    private suspend fun categories() = categoriesCache
        ?: repo.categoryDao.all().associateBy { it.id }.also { categoriesCache = it }
    private suspend fun emis() = emisCache ?: repo.emiDao.all().associateBy { it.id }.also { emisCache = it }
    private suspend fun payments() = paymentsCache
        ?: repo.emiDao.allPayments().groupBy { it.emiId }.also { paymentsCache = it }
    private suspend fun people() = peopleCache ?: repo.loanDao.allPeople().associateBy { it.id }.also { peopleCache = it }
    private suspend fun loans() = loansCache
        ?: repo.loanDao.observeLoans().first().associateBy { it.loan.id }.also { loansCache = it }

    private suspend fun accountName(id: Long?) = id?.let { accounts()[it]?.account?.name }.orEmpty()

    /** Row for one entity, or null when it no longer exists (treat as delete). */
    suspend fun row(entity: String, uuid: String): SheetRow? = when (entity) {
        SyncEntity.TXN -> repo.txnDao.byUuid(uuid)?.let { txn(it) }
        SyncEntity.ACCOUNT -> repo.accountDao.byUuid(uuid)?.let { a -> accounts()[a.id]?.let { account(it) } }
        SyncEntity.CATEGORY -> repo.categoryDao.byUuid(uuid)?.let { category(it) }
        SyncEntity.EMI -> repo.emiDao.byUuid(uuid)?.let { emi(it) }
        SyncEntity.EMI_PAYMENT -> repo.emiDao.paymentByUuid(uuid)?.let { payment(it) }
        SyncEntity.PERSON -> repo.loanDao.personByUuid(uuid)?.let { person(it) }
        SyncEntity.LOAN -> repo.loanDao.loanWithStatsByUuid(uuid)?.let { loan(it) }
        SyncEntity.LOAN_ENTRY -> repo.loanDao.entryByUuid(uuid)?.let { entry(it) }
        else -> null
    }

    /** All rows of an entity. Transactions are limited to [from]..[to]. */
    suspend fun rows(entity: String, from: Long = 0, to: Long = Long.MAX_VALUE): List<SheetRow> = when (entity) {
        SyncEntity.TXN -> repo.txnDao.all().filter { it.date in from..to }.sortedByDescending { it.date }.map { txn(it) }
        SyncEntity.ACCOUNT -> accounts().values.sortedWith(compareBy({ it.account.archived }, { it.account.sortOrder })).map { account(it) }
        SyncEntity.CATEGORY -> categories().values.sortedWith(compareBy({ it.kind }, { it.sortOrder })).map { category(it) }
        SyncEntity.EMI -> emis().values.map { emi(it) }
        SyncEntity.EMI_PAYMENT -> repo.emiDao.allPayments().sortedBy { it.paidDate }.map { payment(it) }
        SyncEntity.PERSON -> people().values.sortedBy { it.name.lowercase() }.map { person(it) }
        SyncEntity.LOAN -> loans().values.map { loan(it) }
        SyncEntity.LOAN_ENTRY -> repo.loanDao.allEntries().sortedBy { it.date }.map { entry(it) }
        else -> emptyList()
    }

    /** UUIDs of all accounts (balances change whenever a transaction changes). */
    suspend fun allAccountUuids(): List<String> = accounts().values.map { it.account.uuid }

    suspend fun txn(t: Txn): SheetRow {
        val loan = t.loanId?.let { loans()[it] }
        return linkedMapOf(
            "ID" to t.uuid,
            "Date" to Dates.formatIso(t.date),
            "Month" to Dates.yearMonth(t.date).toString(),
            "Type" to t.type.label,
            "Amount" to money(t.amount),
            "Category" to (t.categoryId?.let { categories()[it]?.name } ?: ""),
            "Account" to accountName(t.accountId),
            "To Account" to accountName(t.toAccountId),
            "Payee" to t.payee,
            "Note" to t.note,
            "Payment Mode" to t.paymentMode.label,
            "EMI" to (t.emiId?.let { emis()[it]?.name } ?: ""),
            "Loan" to (loan?.let { "${it.personName} · ${direction(it.loan.direction)}" } ?: ""),
            "Updated" to Dates.formatIso(t.updatedAt),
        )
    }

    fun account(a: AccountWithBalance): SheetRow {
        val acc: Account = a.account
        return linkedMapOf(
            "ID" to acc.uuid,
            "Name" to acc.name,
            "Type" to acc.type.label,
            "Institution" to acc.institution,
            "Last 4" to acc.last4,
            "Opening Balance" to money(acc.openingBalance),
            "Current Balance" to money(a.balance),
            "In Net Worth" to yesNo(acc.includeInTotal),
            "Archived" to yesNo(acc.archived),
        )
    }

    fun category(c: Category): SheetRow = linkedMapOf(
        "ID" to c.uuid,
        "Name" to c.name,
        "Kind" to if (c.kind.name == "EXPENSE") "Expense" else "Income",
        "Emoji" to c.emoji,
        "Monthly Budget" to money(c.monthlyBudget),
        "Archived" to yesNo(c.archived),
    )

    suspend fun emi(e: Emi): SheetRow {
        val p = EmiMath.progress(e, payments()[e.id].orEmpty())
        return linkedMapOf(
            "ID" to e.uuid,
            "Name" to e.name,
            "Lender" to e.lender,
            "Principal" to money(e.principal),
            "Rate %" to e.annualRate,
            "Tenure" to e.tenureMonths,
            "EMI Amount" to money(e.emiAmount),
            "First Due" to Dates.formatIsoDay(e.firstDueDate),
            "Next Due" to (p.nextDueDate?.let { Dates.formatIsoDay(it) } ?: ""),
            "Paid" to p.paidCount,
            "Remaining" to p.remainingCount,
            "Amount Paid" to money(p.amountPaid),
            "Amount Remaining" to money(p.amountRemaining),
            "Principal Outstanding" to money(p.principalOutstanding),
            "Status" to if (p.isClosed) "Closed" else if (p.isOverdue) "Overdue" else "Active",
            "Account" to accountName(e.accountId),
            "Auto Pay" to yesNo(e.autoPay),
        )
    }

    suspend fun payment(p: EmiPayment): SheetRow = linkedMapOf(
        "ID" to p.uuid,
        "EMI" to (emis()[p.emiId]?.name ?: ""),
        "Installment" to p.installmentNo,
        "Due Date" to Dates.formatIsoDay(p.dueDate),
        "Paid Date" to Dates.formatIsoDay(p.paidDate),
        "Amount" to money(p.amount),
        "Note" to p.note,
    )

    suspend fun person(p: Person): SheetRow {
        val net = loans().values.filter { it.loan.personId == p.id && it.loan.status == LoanStatus.OPEN }
            .sumOf { if (it.loan.direction == LoanDirection.LENT) it.outstanding else -it.outstanding }
        return linkedMapOf("ID" to p.uuid, "Name" to p.name, "Phone" to p.phone, "Net" to money(net))
    }

    suspend fun loan(l: LoanWithStats): SheetRow = linkedMapOf(
        "ID" to l.loan.uuid,
        "Person" to l.personName,
        "Direction" to direction(l.loan.direction),
        "Principal" to money(l.loan.principal),
        "Top-ups" to money(l.topups),
        "Repaid" to money(l.repaid),
        "Outstanding" to money(l.outstanding),
        "Rate %" to l.loan.annualRate,
        "Start" to Dates.formatIsoDay(l.loan.startDate),
        "Due" to (l.loan.dueDate?.let { Dates.formatIsoDay(it) } ?: ""),
        "Status" to if (l.loan.status == LoanStatus.SETTLED) "Settled" else "Open",
        "Account" to accountName(l.loan.accountId),
        "Note" to l.loan.note,
    )

    suspend fun entry(e: LoanEntry): SheetRow {
        val loan = loans()[e.loanId]
        return linkedMapOf(
            "ID" to e.uuid,
            "Person" to (loan?.personName ?: ""),
            "Loan Direction" to (loan?.let { direction(it.loan.direction) } ?: ""),
            "Kind" to e.kind.label,
            "Amount" to money(e.amount),
            "Date" to Dates.formatIsoDay(e.date),
            "Account" to accountName(e.accountId),
            "Note" to e.note,
        )
    }
}
