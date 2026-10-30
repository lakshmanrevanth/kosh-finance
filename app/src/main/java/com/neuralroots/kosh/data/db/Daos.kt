package com.neuralroots.kosh.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

private const val BALANCE_SELECT = """
SELECT a.*, a.openingBalance
 + COALESCE((SELECT SUM(t.amount) FROM transactions t WHERE t.accountId = a.id AND t.type IN ('INCOME','LOAN_IN')), 0)
 - COALESCE((SELECT SUM(t.amount) FROM transactions t WHERE t.accountId = a.id AND t.type IN ('EXPENSE','LOAN_OUT','TRANSFER')), 0)
 + COALESCE((SELECT SUM(t.amount) FROM transactions t WHERE t.toAccountId = a.id AND t.type = 'TRANSFER'), 0)
 AS balance
FROM accounts a
"""

@Dao
interface AccountDao {
    @Query(BALANCE_SELECT + " WHERE a.archived = 0 ORDER BY a.sortOrder, a.name")
    fun observeWithBalance(): Flow<List<AccountWithBalance>>

    @Query(BALANCE_SELECT + " ORDER BY a.archived, a.sortOrder, a.name")
    fun observeAllWithBalance(): Flow<List<AccountWithBalance>>

    @Query(BALANCE_SELECT + " WHERE a.id = :id")
    fun observeWithBalance(id: Long): Flow<AccountWithBalance?>

    @Query(BALANCE_SELECT + " WHERE a.archived = 0 ORDER BY a.sortOrder, a.name")
    suspend fun withBalance(): List<AccountWithBalance>

    @Query("SELECT * FROM accounts WHERE archived = 0 ORDER BY sortOrder, name")
    fun observeActive(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE archived = 0 ORDER BY sortOrder, name")
    suspend fun active(): List<Account>

    @Query("SELECT * FROM accounts ORDER BY sortOrder, name")
    suspend fun all(): List<Account>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: Long): Account?

    @Query("SELECT * FROM accounts WHERE uuid = :uuid")
    suspend fun byUuid(uuid: String): Account?

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Insert suspend fun insert(a: Account): Long
    @Update suspend fun update(a: Account)
    @Delete suspend fun delete(a: Account)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE archived = 0 ORDER BY kind, sortOrder, name")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE archived = 0 AND kind = :kind ORDER BY sortOrder, name")
    fun observeByKind(kind: CategoryKind): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE archived = 0 AND kind = :kind ORDER BY sortOrder, name")
    suspend fun byKind(kind: CategoryKind): List<Category>

    @Query("SELECT * FROM categories ORDER BY kind, sortOrder, name")
    suspend fun all(): List<Category>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: Long): Category?

    @Query("SELECT * FROM categories WHERE uuid = :uuid")
    suspend fun byUuid(uuid: String): Category?

    @Query("SELECT * FROM categories WHERE name = :name AND kind = :kind LIMIT 1")
    suspend fun findByName(name: String, kind: CategoryKind): Category?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert suspend fun insert(c: Category): Long
    @Insert suspend fun insertAll(c: List<Category>)
    @Update suspend fun update(c: Category)
    @Delete suspend fun delete(c: Category)
}

private const val TXN_ROW_SELECT = """
SELECT t.*, c.name AS categoryName, c.emoji AS categoryEmoji, c.color AS categoryColor,
       a.name AS accountName, b.name AS toAccountName
FROM transactions t
LEFT JOIN categories c ON c.id = t.categoryId
LEFT JOIN accounts a ON a.id = t.accountId
LEFT JOIN accounts b ON b.id = t.toAccountId
"""

@Dao
interface TxnDao {
    /**
     * Filtered transaction list. Pass null to skip a filter.
     * [type] is a TxType name. [q] matches payee, note, or category name.
     */
    @Query(
        TXN_ROW_SELECT + """
        WHERE t.date BETWEEN :from AND :to
          AND (:type IS NULL OR t.type = :type)
          AND (:accountId IS NULL OR t.accountId = :accountId OR t.toAccountId = :accountId)
          AND (:categoryId IS NULL OR t.categoryId = :categoryId)
          AND (:q IS NULL OR t.note LIKE '%' || :q || '%' OR t.payee LIKE '%' || :q || '%' OR c.name LIKE '%' || :q || '%')
        ORDER BY t.date DESC, t.id DESC
        LIMIT :limit"""
    )
    fun observeRows(
        from: Long,
        to: Long,
        type: String? = null,
        accountId: Long? = null,
        categoryId: Long? = null,
        q: String? = null,
        limit: Int = 5000,
    ): Flow<List<TxnRow>>

    @Query(TXN_ROW_SELECT + " ORDER BY t.date DESC, t.id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TxnRow>>

    @Query(TXN_ROW_SELECT + " WHERE t.emiId = :emiId ORDER BY t.date DESC")
    fun observeForEmi(emiId: Long): Flow<List<TxnRow>>

    @Query(TXN_ROW_SELECT + " WHERE t.loanId = :loanId ORDER BY t.date DESC")
    fun observeForLoan(loanId: Long): Flow<List<TxnRow>>

    @Query(TXN_ROW_SELECT + " WHERE t.uuid = :uuid")
    suspend fun rowByUuid(uuid: String): TxnRow?

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: Long): Txn?

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observe(id: Long): Flow<Txn?>

    @Query("SELECT * FROM transactions WHERE uuid = :uuid")
    suspend fun byUuid(uuid: String): Txn?

    @Query("SELECT * FROM transactions WHERE loanId = :loanId")
    suspend fun forLoan(loanId: Long): List<Txn>

    @Query("SELECT * FROM transactions ORDER BY date")
    suspend fun all(): List<Txn>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = :type AND date BETWEEN :from AND :to")
    fun observeTotal(type: TxType, from: Long, to: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = :type AND date BETWEEN :from AND :to")
    suspend fun total(type: TxType, from: Long, to: Long): Long

    @Query(
        """SELECT c.id AS categoryId, c.name AS name, c.emoji AS emoji, c.color AS color,
                  c.monthlyBudget AS budget, SUM(t.amount) AS total
           FROM transactions t JOIN categories c ON c.id = t.categoryId
           WHERE t.type = :type AND t.date BETWEEN :from AND :to
           GROUP BY c.id ORDER BY total DESC"""
    )
    fun observeCategoryTotals(type: TxType, from: Long, to: Long): Flow<List<CategoryTotal>>

    @Query(
        """SELECT strftime('%Y-%m', date / 1000, 'unixepoch', 'localtime') AS period,
                  SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END) AS expense,
                  SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END) AS income
           FROM transactions WHERE date BETWEEN :from AND :to
           GROUP BY period ORDER BY period"""
    )
    fun observeMonthly(from: Long, to: Long): Flow<List<PeriodTotal>>

    @Query(
        """SELECT strftime('%Y-%m-%d', date / 1000, 'unixepoch', 'localtime') AS period,
                  SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END) AS expense,
                  SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END) AS income
           FROM transactions WHERE date BETWEEN :from AND :to
           GROUP BY period ORDER BY period"""
    )
    fun observeDaily(from: Long, to: Long): Flow<List<PeriodTotal>>

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId OR toAccountId = :accountId")
    suspend fun countForAccount(accountId: Long): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId")
    suspend fun countForCategory(categoryId: Long): Int

    @Insert suspend fun insert(t: Txn): Long
    @Update suspend fun update(t: Txn)
    @Delete suspend fun delete(t: Txn)
}

@Dao
interface EmiDao {
    @Query("SELECT * FROM emis ORDER BY status, firstDueDate")
    fun observeAll(): Flow<List<Emi>>

    @Query("SELECT * FROM emis WHERE id = :id")
    fun observe(id: Long): Flow<Emi?>

    @Query("SELECT * FROM emis WHERE id = :id")
    suspend fun get(id: Long): Emi?

    @Query("SELECT * FROM emis WHERE uuid = :uuid")
    suspend fun byUuid(uuid: String): Emi?

    @Query("SELECT * FROM emis WHERE status = 'ACTIVE'")
    suspend fun active(): List<Emi>

    @Query("SELECT * FROM emis")
    suspend fun all(): List<Emi>

    @Insert suspend fun insert(e: Emi): Long
    @Update suspend fun update(e: Emi)
    @Delete suspend fun delete(e: Emi)

    @Query("SELECT * FROM emi_payments WHERE emiId = :emiId ORDER BY installmentNo")
    fun observePayments(emiId: Long): Flow<List<EmiPayment>>

    @Query("SELECT * FROM emi_payments ORDER BY paidDate")
    fun observeAllPayments(): Flow<List<EmiPayment>>

    @Query("SELECT * FROM emi_payments WHERE emiId = :emiId ORDER BY installmentNo")
    suspend fun paymentsFor(emiId: Long): List<EmiPayment>

    @Query("SELECT * FROM emi_payments")
    suspend fun allPayments(): List<EmiPayment>

    @Query("SELECT * FROM emi_payments WHERE id = :id")
    suspend fun getPayment(id: Long): EmiPayment?

    @Query("SELECT * FROM emi_payments WHERE uuid = :uuid")
    suspend fun paymentByUuid(uuid: String): EmiPayment?

    @Query("SELECT * FROM emi_payments WHERE transactionId = :txnId")
    suspend fun paymentByTxn(txnId: Long): EmiPayment?

    @Query("SELECT * FROM emi_payments WHERE emiId = :emiId AND installmentNo = :n")
    suspend fun payment(emiId: Long, n: Int): EmiPayment?

    @Insert suspend fun insertPayment(p: EmiPayment): Long
    @Update suspend fun updatePayment(p: EmiPayment)
    @Delete suspend fun deletePayment(p: EmiPayment)
}

private const val LOAN_STATS_SELECT = """
SELECT l.*, p.name AS personName,
  COALESCE((SELECT SUM(e.amount) FROM loan_entries e WHERE e.loanId = l.id AND e.kind = 'REPAYMENT'), 0) AS repaid,
  COALESCE((SELECT SUM(e.amount) FROM loan_entries e WHERE e.loanId = l.id AND e.kind = 'TOPUP'), 0) AS topups
FROM loans l JOIN people p ON p.id = l.personId
"""

@Dao
interface LoanDao {
    // People
    @Query("SELECT * FROM people ORDER BY name COLLATE NOCASE")
    fun observePeople(): Flow<List<Person>>

    @Query("SELECT * FROM people WHERE id = :id")
    fun observePerson(id: Long): Flow<Person?>

    @Query("SELECT * FROM people WHERE id = :id")
    suspend fun getPerson(id: Long): Person?

    @Query("SELECT * FROM people WHERE uuid = :uuid")
    suspend fun personByUuid(uuid: String): Person?

    @Query("SELECT * FROM people WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun personByName(name: String): Person?

    @Query("SELECT * FROM people")
    suspend fun allPeople(): List<Person>

    @Insert suspend fun insertPerson(p: Person): Long
    @Update suspend fun updatePerson(p: Person)
    @Delete suspend fun deletePerson(p: Person)

    // Loans
    @Query(LOAN_STATS_SELECT + " ORDER BY l.status, l.startDate DESC")
    fun observeLoans(): Flow<List<LoanWithStats>>

    @Query(LOAN_STATS_SELECT + " WHERE l.personId = :personId ORDER BY l.status, l.startDate DESC")
    fun observeLoansForPerson(personId: Long): Flow<List<LoanWithStats>>

    @Query(LOAN_STATS_SELECT + " WHERE l.id = :id")
    fun observeLoan(id: Long): Flow<LoanWithStats?>

    @Query(LOAN_STATS_SELECT + " WHERE l.id = :id")
    suspend fun loanWithStats(id: Long): LoanWithStats?

    @Query(LOAN_STATS_SELECT + " WHERE l.uuid = :uuid")
    suspend fun loanWithStatsByUuid(uuid: String): LoanWithStats?

    @Query(LOAN_STATS_SELECT + " WHERE l.status = 'OPEN'")
    suspend fun openLoans(): List<LoanWithStats>

    @Query("SELECT * FROM loans WHERE id = :id")
    suspend fun getLoan(id: Long): Loan?

    @Query("SELECT * FROM loans")
    suspend fun allLoans(): List<Loan>

    @Query("SELECT COUNT(*) FROM loans WHERE personId = :personId")
    suspend fun loanCountForPerson(personId: Long): Int

    @Insert suspend fun insertLoan(l: Loan): Long
    @Update suspend fun updateLoan(l: Loan)
    @Delete suspend fun deleteLoan(l: Loan)

    // Entries
    @Query("SELECT * FROM loan_entries WHERE loanId = :loanId ORDER BY date DESC, id DESC")
    fun observeEntries(loanId: Long): Flow<List<LoanEntry>>

    @Query("SELECT * FROM loan_entries WHERE loanId = :loanId")
    suspend fun entriesFor(loanId: Long): List<LoanEntry>

    @Query("SELECT * FROM loan_entries")
    suspend fun allEntries(): List<LoanEntry>

    @Query("SELECT * FROM loan_entries WHERE id = :id")
    suspend fun getEntry(id: Long): LoanEntry?

    @Query("SELECT * FROM loan_entries WHERE uuid = :uuid")
    suspend fun entryByUuid(uuid: String): LoanEntry?

    @Query("SELECT * FROM loan_entries WHERE transactionId = :txnId")
    suspend fun entryByTxn(txnId: Long): LoanEntry?

    @Insert suspend fun insertEntry(e: LoanEntry): Long
    @Update suspend fun updateEntry(e: LoanEntry)
    @Delete suspend fun deleteEntry(e: LoanEntry)
}

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(item: OutboxItem)

    @Query("SELECT * FROM outbox ORDER BY id LIMIT :limit")
    suspend fun pending(limit: Int): List<OutboxItem>

    @Query("DELETE FROM outbox WHERE id IN (:ids)")
    suspend fun remove(ids: List<Long>)

    @Query("UPDATE outbox SET attempts = attempts + 1, lastError = :error WHERE id IN (:ids)")
    suspend fun markFailed(ids: List<Long>, error: String)

    @Query("SELECT COUNT(*) FROM outbox")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM outbox")
    suspend fun count(): Int

    @Query("DELETE FROM outbox")
    suspend fun clear()
}

@Dao
interface CloudOutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(item: CloudOutboxItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAll(items: List<CloudOutboxItem>)

    @Query("SELECT * FROM cloud_outbox ORDER BY id LIMIT :limit")
    suspend fun pending(limit: Int): List<CloudOutboxItem>

    @Query("DELETE FROM cloud_outbox WHERE id IN (:ids)")
    suspend fun remove(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM cloud_outbox")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM cloud_outbox")
    suspend fun count(): Int

    @Query("DELETE FROM cloud_outbox")
    suspend fun clear()
}
