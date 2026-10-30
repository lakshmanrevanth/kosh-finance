package com.neuralroots.kosh.data.repo

import androidx.room.withTransaction
import com.neuralroots.kosh.data.db.Account
import com.neuralroots.kosh.data.db.AccountType
import com.neuralroots.kosh.data.db.Category
import com.neuralroots.kosh.data.db.CloudOutboxItem
import com.neuralroots.kosh.cloud.CloudMapper
import com.neuralroots.kosh.data.db.CategoryKind
import com.neuralroots.kosh.data.db.Emi
import com.neuralroots.kosh.data.db.EmiPayment
import com.neuralroots.kosh.data.db.EmiStatus
import com.neuralroots.kosh.data.db.KoshDatabase
import com.neuralroots.kosh.data.db.Loan
import com.neuralroots.kosh.data.db.LoanDirection
import com.neuralroots.kosh.data.db.LoanEntry
import com.neuralroots.kosh.data.db.LoanEntryKind
import com.neuralroots.kosh.data.db.LoanStatus
import com.neuralroots.kosh.data.db.OutboxItem
import com.neuralroots.kosh.data.db.PaymentMode
import com.neuralroots.kosh.data.db.Person
import com.neuralroots.kosh.data.db.SyncEntity
import com.neuralroots.kosh.data.db.TxType
import com.neuralroots.kosh.data.db.Txn
import com.neuralroots.kosh.data.db.TxnRow
import com.neuralroots.kosh.data.db.nowMs
import com.neuralroots.kosh.util.EmiMath
import com.neuralroots.kosh.util.EmiProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Side effects run after every successful write (sync kick + widget refresh). */
fun interface RepoEffects {
    fun onDataChanged()
}

/**
 * Single entry point for all reads and writes. Every write:
 *  1. runs in a DB transaction,
 *  2. queues the changed rows in the sync outbox,
 *  3. calls [RepoEffects.onDataChanged].
 */
class FinanceRepository(
    val db: KoshDatabase,
    private val effects: RepoEffects,
) {
    val accountDao = db.accountDao()
    val categoryDao = db.categoryDao()
    val txnDao = db.txnDao()
    val emiDao = db.emiDao()
    val loanDao = db.loanDao()
    val outboxDao = db.outboxDao()
    val cloudOutboxDao = db.cloudOutboxDao()

    private suspend fun queue(entity: String, uuid: String, op: String = SyncEntity.OP_UPSERT) {
        outboxDao.put(OutboxItem(entity = entity, uuid = uuid, op = op))
        CloudMapper.collectionFor(entity)?.let { col ->
            cloudOutboxDao.put(CloudOutboxItem(collection = col, uuid = uuid, deleted = op == SyncEntity.OP_DELETE))
        }
    }

    private suspend fun <T> write(block: suspend () -> T): T {
        val r = db.withTransaction { block() }
        effects.onDataChanged()
        return r
    }

    // ---------------------------------------------------------------- Seeding

    suspend fun seedIfEmpty() {
        if (categoryDao.count() == 0) write {
            DefaultData.categories().forEach { c ->
                categoryDao.insert(c); queue(SyncEntity.CATEGORY, c.uuid)
            }
        }
        if (accountDao.count() == 0) write {
            listOf(
                Account(name = "Bank Account", type = AccountType.BANK, color = 0xFF5B5BD6.toInt(), sortOrder = 0),
                Account(name = "Cash", type = AccountType.CASH, color = 0xFF12B76A.toInt(), sortOrder = 1),
            ).forEach { a -> accountDao.insert(a); queue(SyncEntity.ACCOUNT, a.uuid) }
        }
    }

    // ---------------------------------------------------------------- Accounts

    fun observeAccounts() = accountDao.observeWithBalance()
    fun observeAllAccounts() = accountDao.observeAllWithBalance()
    fun observeAccount(id: Long) = accountDao.observeWithBalance(id)
    fun observeActiveAccounts() = accountDao.observeActive()

    suspend fun saveAccount(a: Account): Long = write {
        val id = if (a.id == 0L) accountDao.insert(a) else {
            accountDao.update(a.copy(updatedAt = nowMs())); a.id
        }
        queue(SyncEntity.ACCOUNT, a.uuid)
        id
    }

    /** Deletes the account if it has no transactions, otherwise archives it. Returns true if deleted. */
    suspend fun deleteOrArchiveAccount(id: Long): Boolean = write {
        val a = accountDao.get(id) ?: return@write false
        if (txnDao.countForAccount(id) == 0) {
            accountDao.delete(a); queue(SyncEntity.ACCOUNT, a.uuid, SyncEntity.OP_DELETE); true
        } else {
            accountDao.update(a.copy(archived = true, updatedAt = nowMs())); queue(SyncEntity.ACCOUNT, a.uuid); false
        }
    }

    // ---------------------------------------------------------------- Categories

    fun observeCategories() = categoryDao.observeAll()
    fun observeCategories(kind: CategoryKind) = categoryDao.observeByKind(kind)

    suspend fun saveCategory(c: Category): Long = write {
        val id = if (c.id == 0L) categoryDao.insert(c) else {
            categoryDao.update(c.copy(updatedAt = nowMs())); c.id
        }
        queue(SyncEntity.CATEGORY, c.uuid)
        id
    }

    /** Deletes if unused, otherwise archives. Returns true if deleted. */
    suspend fun deleteOrArchiveCategory(id: Long): Boolean = write {
        val c = categoryDao.get(id) ?: return@write false
        if (txnDao.countForCategory(id) == 0) {
            categoryDao.delete(c); queue(SyncEntity.CATEGORY, c.uuid, SyncEntity.OP_DELETE); true
        } else {
            categoryDao.update(c.copy(archived = true, updatedAt = nowMs())); queue(SyncEntity.CATEGORY, c.uuid); false
        }
    }

    // ---------------------------------------------------------------- Transactions

    fun observeTxns(
        from: Long = 0,
        to: Long = Long.MAX_VALUE,
        type: TxType? = null,
        accountId: Long? = null,
        categoryId: Long? = null,
        query: String? = null,
        limit: Int = 5000,
    ): Flow<List<TxnRow>> = txnDao.observeRows(
        from, to, type?.name, accountId, categoryId, query?.trim()?.takeIf { it.isNotEmpty() }, limit,
    )

    fun observeRecent(limit: Int = 10) = txnDao.observeRecent(limit)
    fun observeTxn(id: Long) = txnDao.observe(id)
    suspend fun getTxn(id: Long) = txnDao.get(id)
    fun observeTotal(type: TxType, from: Long, to: Long) = txnDao.observeTotal(type, from, to)
    suspend fun total(type: TxType, from: Long, to: Long) = txnDao.total(type, from, to)
    fun observeCategoryTotals(type: TxType, from: Long, to: Long) = txnDao.observeCategoryTotals(type, from, to)
    fun observeMonthly(from: Long, to: Long) = txnDao.observeMonthly(from, to)
    fun observeDaily(from: Long, to: Long) = txnDao.observeDaily(from, to)

    /** Insert or update. Returns the id. */
    suspend fun saveTxn(t: Txn): Long = write {
        require(t.amount > 0) { "Amount must be positive" }
        val id = if (t.id == 0L) txnDao.insert(t) else {
            txnDao.update(t.copy(updatedAt = nowMs())); t.id
        }
        queue(SyncEntity.TXN, t.uuid)
        // Keep linked EMI payment / loan entry amounts in step with an edited transaction.
        if (t.id != 0L) {
            emiDao.paymentByTxn(t.id)?.let { p ->
                emiDao.updatePayment(p.copy(amount = t.amount, paidDate = t.date))
                queue(SyncEntity.EMI_PAYMENT, p.uuid)
            }
            loanDao.entryByTxn(t.id)?.let { e ->
                loanDao.updateEntry(e.copy(amount = t.amount, date = t.date, accountId = t.accountId))
                queue(SyncEntity.LOAN_ENTRY, e.uuid)
                refreshLoanStatus(e.loanId)
            }
        }
        id
    }

    suspend fun deleteTxn(id: Long) = write {
        val t = txnDao.get(id) ?: return@write
        txnDao.delete(t)
        queue(SyncEntity.TXN, t.uuid, SyncEntity.OP_DELETE)
        emiDao.paymentByTxn(id)?.let { p ->
            emiDao.deletePayment(p); queue(SyncEntity.EMI_PAYMENT, p.uuid, SyncEntity.OP_DELETE)
            emiDao.get(p.emiId)?.let { reopenIfNeeded(it) }
        }
        loanDao.entryByTxn(id)?.let { e ->
            loanDao.deleteEntry(e); queue(SyncEntity.LOAN_ENTRY, e.uuid, SyncEntity.OP_DELETE)
            refreshLoanStatus(e.loanId)
        }
        t.loanId?.let { lid ->
            loanDao.getLoan(lid)?.takeIf { it.transactionId == id }?.let { l ->
                loanDao.updateLoan(l.copy(transactionId = null, accountId = null, updatedAt = nowMs()))
                queue(SyncEntity.LOAN, l.uuid)
            }
        }
    }

    // ---------------------------------------------------------------- EMIs

    fun observeEmis() = emiDao.observeAll()
    fun observeEmi(id: Long) = emiDao.observe(id)
    fun observeEmiPayments(emiId: Long) = emiDao.observePayments(emiId)
    fun observeEmiTxns(emiId: Long) = txnDao.observeForEmi(emiId)

    /** All EMIs with derived progress, active first then by next due date. */
    fun observeEmiProgress(): Flow<List<EmiProgress>> =
        combine(emiDao.observeAll(), emiDao.observeAllPayments()) { emis, pays ->
            val byEmi = pays.groupBy { it.emiId }
            emis.map { EmiMath.progress(it, byEmi[it.id].orEmpty()) }
                .sortedWith(compareBy<EmiProgress>({ it.isClosed }, { it.nextDueDate ?: Long.MAX_VALUE }))
        }

    fun observeEmiProgress(id: Long): Flow<EmiProgress?> =
        combine(emiDao.observe(id), emiDao.observePayments(id)) { e, p -> e?.let { EmiMath.progress(it, p) } }

    suspend fun saveEmi(e: Emi): Long = write {
        val id = if (e.id == 0L) emiDao.insert(e) else {
            emiDao.update(e.copy(updatedAt = nowMs())); e.id
        }
        queue(SyncEntity.EMI, e.uuid)
        id
    }

    /** Deletes the EMI and its payment records. Expense transactions already recorded are kept. */
    suspend fun deleteEmi(id: Long) = write {
        val e = emiDao.get(id) ?: return@write
        emiDao.paymentsFor(id).forEach { p ->
            emiDao.deletePayment(p); queue(SyncEntity.EMI_PAYMENT, p.uuid, SyncEntity.OP_DELETE)
        }
        emiDao.delete(e)
        queue(SyncEntity.EMI, e.uuid, SyncEntity.OP_DELETE)
    }

    /**
     * Records installment [installmentNo] as paid. When [accountId] is given, an EXPENSE
     * transaction is created so the bank balance and spending reports reflect it.
     */
    suspend fun markEmiPaid(
        emiId: Long,
        installmentNo: Int,
        amount: Long,
        paidDate: Long,
        accountId: Long?,
        note: String = "",
    ): Long = write {
        val emi = emiDao.get(emiId) ?: error("EMI not found")
        emiDao.payment(emiId, installmentNo)?.let { return@write it.id }
        var txnId: Long? = null
        if (accountId != null) {
            val cat = emi.categoryId ?: categoryDao.findByName("EMI", CategoryKind.EXPENSE)?.id
            val t = Txn(
                type = TxType.EXPENSE,
                amount = amount,
                accountId = accountId,
                categoryId = cat,
                payee = emi.lender.ifBlank { emi.name },
                note = "EMI $installmentNo/${emi.tenureMonths} · ${emi.name}",
                date = paidDate,
                paymentMode = PaymentMode.AUTO_DEBIT,
                emiId = emi.id,
            )
            txnId = txnDao.insert(t)
            queue(SyncEntity.TXN, t.uuid)
        }
        val p = EmiPayment(
            emiId = emiId,
            installmentNo = installmentNo,
            dueDate = EmiMath.dueDate(emi, installmentNo),
            paidDate = paidDate,
            amount = amount,
            transactionId = txnId,
            note = note,
        )
        val pid = emiDao.insertPayment(p)
        queue(SyncEntity.EMI_PAYMENT, p.uuid)
        val prog = EmiMath.progress(emi, emiDao.paymentsFor(emiId))
        if (prog.remainingCount == 0 && emi.status == EmiStatus.ACTIVE) {
            emiDao.update(emi.copy(status = EmiStatus.CLOSED, updatedAt = nowMs()))
        }
        queue(SyncEntity.EMI, emi.uuid)
        pid
    }

    /** Undo a payment; also deletes the transaction it created. */
    suspend fun unmarkEmiPayment(paymentId: Long) = write {
        val p = emiDao.getPayment(paymentId) ?: return@write
        p.transactionId?.let { tid ->
            txnDao.get(tid)?.let { t -> txnDao.delete(t); queue(SyncEntity.TXN, t.uuid, SyncEntity.OP_DELETE) }
        }
        emiDao.deletePayment(p)
        queue(SyncEntity.EMI_PAYMENT, p.uuid, SyncEntity.OP_DELETE)
        emiDao.get(p.emiId)?.let { reopenIfNeeded(it) }
    }

    private suspend fun reopenIfNeeded(emi: Emi) {
        val prog = EmiMath.progress(emi, emiDao.paymentsFor(emi.id))
        if (emi.status == EmiStatus.CLOSED && prog.remainingCount > 0) {
            emiDao.update(emi.copy(status = EmiStatus.ACTIVE, updatedAt = nowMs()))
        }
        queue(SyncEntity.EMI, emi.uuid)
    }

    suspend fun setEmiClosed(id: Long, closed: Boolean) = write {
        val e = emiDao.get(id) ?: return@write
        emiDao.update(e.copy(status = if (closed) EmiStatus.CLOSED else EmiStatus.ACTIVE, updatedAt = nowMs()))
        queue(SyncEntity.EMI, e.uuid)
    }

    // ---------------------------------------------------------------- People & loans

    fun observePeople() = loanDao.observePeople()
    fun observePerson(id: Long) = loanDao.observePerson(id)
    fun observeLoans() = loanDao.observeLoans()
    fun observeLoansForPerson(personId: Long) = loanDao.observeLoansForPerson(personId)
    fun observeLoan(id: Long) = loanDao.observeLoan(id)
    fun observeLoanEntries(loanId: Long) = loanDao.observeEntries(loanId)

    suspend fun savePerson(p: Person): Long = write {
        val id = if (p.id == 0L) loanDao.insertPerson(p) else {
            loanDao.updatePerson(p.copy(updatedAt = nowMs())); p.id
        }
        queue(SyncEntity.PERSON, p.uuid)
        id
    }

    /** Finds a person by name (case-insensitive) or creates one. */
    suspend fun personFor(name: String): Long {
        loanDao.personByName(name.trim())?.let { return it.id }
        return savePerson(Person(name = name.trim()))
    }

    /** Returns false if the person still has loans. */
    suspend fun deletePerson(id: Long): Boolean = write {
        val p = loanDao.getPerson(id) ?: return@write false
        if (loanDao.loanCountForPerson(id) > 0) return@write false
        loanDao.deletePerson(p); queue(SyncEntity.PERSON, p.uuid, SyncEntity.OP_DELETE); true
    }

    /**
     * Saves a loan. When [accountId] is non-null, the money movement is recorded as a
     * LOAN_OUT (lent) or LOAN_IN (borrowed) transaction on that account.
     */
    suspend fun saveLoan(loan: Loan, accountId: Long?): Long = write {
        val person = loanDao.getPerson(loan.personId)
        val txnType = if (loan.direction == LoanDirection.LENT) TxType.LOAN_OUT else TxType.LOAN_IN
        val note = if (loan.direction == LoanDirection.LENT) "Lent to ${person?.name}" else "Borrowed from ${person?.name}"
        var l = loan.copy(accountId = accountId, updatedAt = nowMs())
        val id = if (l.id == 0L) loanDao.insertLoan(l) else { loanDao.updateLoan(l); l.id }
        l = l.copy(id = id)

        val existing = l.transactionId?.let { txnDao.get(it) }
        when {
            accountId == null && existing != null -> {
                txnDao.delete(existing); queue(SyncEntity.TXN, existing.uuid, SyncEntity.OP_DELETE)
                l = l.copy(transactionId = null); loanDao.updateLoan(l)
            }
            accountId != null && existing != null -> {
                val t = existing.copy(
                    type = txnType, amount = l.principal, accountId = accountId, date = l.startDate,
                    payee = person?.name ?: "", note = note, updatedAt = nowMs(),
                )
                txnDao.update(t); queue(SyncEntity.TXN, t.uuid)
            }
            accountId != null && l.principal > 0 -> {
                val t = Txn(
                    type = txnType, amount = l.principal, accountId = accountId, date = l.startDate,
                    payee = person?.name ?: "", note = note, paymentMode = PaymentMode.UPI, loanId = id,
                )
                val tid = txnDao.insert(t); queue(SyncEntity.TXN, t.uuid)
                l = l.copy(transactionId = tid); loanDao.updateLoan(l)
            }
        }
        queue(SyncEntity.LOAN, l.uuid)
        refreshLoanStatus(id)
        id
    }

    /**
     * Adds a repayment or top-up. With an [accountId], the matching money movement is recorded:
     *  LENT + REPAYMENT -> money in, BORROWED + REPAYMENT -> money out, top-ups the opposite.
     */
    suspend fun addLoanEntry(
        loanId: Long,
        kind: LoanEntryKind,
        amount: Long,
        date: Long,
        accountId: Long?,
        note: String = "",
    ): Long = write {
        val loan = loanDao.getLoan(loanId) ?: error("Loan not found")
        val person = loanDao.getPerson(loan.personId)
        var txnId: Long? = null
        if (accountId != null) {
            val moneyIn = (loan.direction == LoanDirection.LENT) == (kind == LoanEntryKind.REPAYMENT)
            val label = when {
                kind == LoanEntryKind.REPAYMENT && loan.direction == LoanDirection.LENT -> "Repayment from ${person?.name}"
                kind == LoanEntryKind.REPAYMENT -> "Repaid to ${person?.name}"
                loan.direction == LoanDirection.LENT -> "Lent more to ${person?.name}"
                else -> "Borrowed more from ${person?.name}"
            }
            val t = Txn(
                type = if (moneyIn) TxType.LOAN_IN else TxType.LOAN_OUT,
                amount = amount, accountId = accountId, date = date,
                payee = person?.name ?: "", note = listOf(label, note).filter { it.isNotBlank() }.joinToString(" · "),
                loanId = loanId,
            )
            txnId = txnDao.insert(t); queue(SyncEntity.TXN, t.uuid)
        }
        val e = LoanEntry(loanId = loanId, kind = kind, amount = amount, date = date, accountId = accountId, transactionId = txnId, note = note)
        val id = loanDao.insertEntry(e)
        queue(SyncEntity.LOAN_ENTRY, e.uuid)
        refreshLoanStatus(loanId)
        id
    }

    suspend fun deleteLoanEntry(entryId: Long) = write {
        val e = loanDao.getEntry(entryId) ?: return@write
        e.transactionId?.let { tid ->
            txnDao.get(tid)?.let { t -> txnDao.delete(t); queue(SyncEntity.TXN, t.uuid, SyncEntity.OP_DELETE) }
        }
        loanDao.deleteEntry(e); queue(SyncEntity.LOAN_ENTRY, e.uuid, SyncEntity.OP_DELETE)
        refreshLoanStatus(e.loanId)
    }

    /** Deletes the loan, its entries and every transaction linked to it. */
    suspend fun deleteLoan(loanId: Long) = write {
        val l = loanDao.getLoan(loanId) ?: return@write
        txnDao.forLoan(loanId).forEach { t -> txnDao.delete(t); queue(SyncEntity.TXN, t.uuid, SyncEntity.OP_DELETE) }
        loanDao.entriesFor(loanId).forEach { e -> loanDao.deleteEntry(e); queue(SyncEntity.LOAN_ENTRY, e.uuid, SyncEntity.OP_DELETE) }
        loanDao.deleteLoan(l); queue(SyncEntity.LOAN, l.uuid, SyncEntity.OP_DELETE)
    }

    /** Manually settle (e.g. forgiven) or reopen. */
    suspend fun setLoanSettled(loanId: Long, settled: Boolean) = write {
        val l = loanDao.getLoan(loanId) ?: return@write
        loanDao.updateLoan(l.copy(status = if (settled) LoanStatus.SETTLED else LoanStatus.OPEN, updatedAt = nowMs()))
        queue(SyncEntity.LOAN, l.uuid)
    }

    private suspend fun refreshLoanStatus(loanId: Long) {
        val s = loanDao.loanWithStats(loanId) ?: return
        val shouldBe = if (s.outstanding <= 0) LoanStatus.SETTLED else LoanStatus.OPEN
        if (s.loan.status != shouldBe) loanDao.updateLoan(s.loan.copy(status = shouldBe, updatedAt = nowMs()))
        queue(SyncEntity.LOAN, s.loan.uuid)
    }

    // ---------------------------------------------------------------- Sync

    fun observePendingSync() = outboxDao.observeCount()

    /** Queues every row for upload (first-time setup or "Resync all"). */
    suspend fun queueFullResync() = write {
        accountDao.all().forEach { queue(SyncEntity.ACCOUNT, it.uuid) }
        categoryDao.all().forEach { queue(SyncEntity.CATEGORY, it.uuid) }
        txnDao.all().forEach { queue(SyncEntity.TXN, it.uuid) }
        emiDao.all().forEach { queue(SyncEntity.EMI, it.uuid) }
        emiDao.allPayments().forEach { queue(SyncEntity.EMI_PAYMENT, it.uuid) }
        loanDao.allPeople().forEach { queue(SyncEntity.PERSON, it.uuid) }
        loanDao.allLoans().forEach { queue(SyncEntity.LOAN, it.uuid) }
        loanDao.allEntries().forEach { queue(SyncEntity.LOAN_ENTRY, it.uuid) }
    }
}
