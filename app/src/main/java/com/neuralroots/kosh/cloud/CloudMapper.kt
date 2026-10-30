package com.neuralroots.kosh.cloud

import com.neuralroots.kosh.data.db.Account
import com.neuralroots.kosh.data.db.AccountType
import com.neuralroots.kosh.data.db.Category
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
import com.neuralroots.kosh.data.db.PaymentMode
import com.neuralroots.kosh.data.db.Person
import com.neuralroots.kosh.data.db.SyncEntity
import com.neuralroots.kosh.data.db.TxType
import com.neuralroots.kosh.data.db.Txn

/**
 * Converts Room rows to Firestore documents and back.
 *
 * Firestore layout: users/{uid}/{collection}/{uuid}
 * Local Long ids are never stored in the cloud — references are stored as the referenced
 * row's uuid (accountUuid, categoryUuid, …) and resolved back to local ids on restore,
 * so data restores correctly onto a fresh install.
 */
object CloudMapper {
    const val ACCOUNTS = "accounts"
    const val CATEGORIES = "categories"
    const val TRANSACTIONS = "transactions"
    const val EMIS = "emis"
    const val EMI_PAYMENTS = "emi_payments"
    const val PEOPLE = "people"
    const val LOANS = "loans"
    const val LOAN_ENTRIES = "loan_entries"

    /** Restore order: a row's references must exist before it is applied. */
    val applyOrder = listOf(ACCOUNTS, CATEGORIES, PEOPLE, EMIS, LOANS, TRANSACTIONS, EMI_PAYMENTS, LOAN_ENTRIES)

    fun collectionFor(syncEntity: String): String? = when (syncEntity) {
        SyncEntity.ACCOUNT -> ACCOUNTS
        SyncEntity.CATEGORY -> CATEGORIES
        SyncEntity.TXN -> TRANSACTIONS
        SyncEntity.EMI -> EMIS
        SyncEntity.EMI_PAYMENT -> EMI_PAYMENTS
        SyncEntity.PERSON -> PEOPLE
        SyncEntity.LOAN -> LOANS
        SyncEntity.LOAN_ENTRY -> LOAN_ENTRIES
        else -> null
    }

    // ------------------------------------------------------------------ helpers

    private fun Map<String, Any?>.str(k: String): String = this[k] as? String ?: ""
    private fun Map<String, Any?>.strOrNull(k: String): String? = (this[k] as? String)?.takeIf { it.isNotEmpty() }
    private fun Map<String, Any?>.long(k: String): Long = (this[k] as? Number)?.toLong() ?: 0L
    private fun Map<String, Any?>.longOrNull(k: String): Long? = (this[k] as? Number)?.toLong()
    private fun Map<String, Any?>.int(k: String): Int = (this[k] as? Number)?.toInt() ?: 0
    private fun Map<String, Any?>.dbl(k: String): Double = (this[k] as? Number)?.toDouble() ?: 0.0
    private fun Map<String, Any?>.bool(k: String, def: Boolean = false): Boolean = this[k] as? Boolean ?: def
    private inline fun <reified E : Enum<E>> Map<String, Any?>.enum(k: String, def: E): E =
        (this[k] as? String)?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: def

    /** Last-modified time of a cloud doc (local clock of the writing device). */
    fun updatedAt(doc: Map<String, Any?>): Long = doc.long("updatedAt").takeIf { it > 0 } ?: doc.long("createdAt")

    // ------------------------------------------------------------------ upload

    /** The document for [uuid] in [collection], or null if the row no longer exists locally. */
    suspend fun toDoc(db: KoshDatabase, collection: String, uuid: String): Map<String, Any?>? {
        val a = db.accountDao(); val c = db.categoryDao(); val t = db.txnDao(); val e = db.emiDao(); val l = db.loanDao()
        suspend fun accU(id: Long?) = id?.let { a.get(it)?.uuid }
        suspend fun catU(id: Long?) = id?.let { c.get(it)?.uuid }
        return when (collection) {
            ACCOUNTS -> a.byUuid(uuid)?.let {
                mapOf(
                    "name" to it.name, "type" to it.type.name, "openingBalance" to it.openingBalance,
                    "institution" to it.institution, "last4" to it.last4, "color" to it.color.toLong(),
                    "includeInTotal" to it.includeInTotal, "archived" to it.archived, "sortOrder" to it.sortOrder,
                    "createdAt" to it.createdAt, "updatedAt" to it.updatedAt,
                )
            }
            CATEGORIES -> c.byUuid(uuid)?.let {
                mapOf(
                    "name" to it.name, "kind" to it.kind.name, "emoji" to it.emoji, "color" to it.color.toLong(),
                    "monthlyBudget" to it.monthlyBudget, "archived" to it.archived, "sortOrder" to it.sortOrder,
                    "createdAt" to it.createdAt, "updatedAt" to it.updatedAt,
                )
            }
            TRANSACTIONS -> t.byUuid(uuid)?.let {
                mapOf(
                    "type" to it.type.name, "amount" to it.amount,
                    "accountUuid" to accU(it.accountId), "toAccountUuid" to accU(it.toAccountId),
                    "categoryUuid" to catU(it.categoryId),
                    "payee" to it.payee, "note" to it.note, "date" to it.date, "paymentMode" to it.paymentMode.name,
                    "emiUuid" to it.emiId?.let { id -> e.get(id)?.uuid },
                    "loanUuid" to it.loanId?.let { id -> l.getLoan(id)?.uuid },
                    "createdAt" to it.createdAt, "updatedAt" to it.updatedAt,
                )
            }
            EMIS -> e.byUuid(uuid)?.let {
                mapOf(
                    "name" to it.name, "lender" to it.lender, "principal" to it.principal, "annualRate" to it.annualRate,
                    "tenureMonths" to it.tenureMonths, "emiAmount" to it.emiAmount, "firstDueDate" to it.firstDueDate,
                    "accountUuid" to accU(it.accountId), "categoryUuid" to catU(it.categoryId),
                    "paidBeforeTracking" to it.paidBeforeTracking, "autoPay" to it.autoPay,
                    "remindDaysBefore" to it.remindDaysBefore, "note" to it.note, "status" to it.status.name,
                    "createdAt" to it.createdAt, "updatedAt" to it.updatedAt,
                )
            }
            EMI_PAYMENTS -> e.paymentByUuid(uuid)?.let {
                mapOf(
                    "emiUuid" to e.get(it.emiId)?.uuid, "installmentNo" to it.installmentNo,
                    "dueDate" to it.dueDate, "paidDate" to it.paidDate, "amount" to it.amount,
                    "transactionUuid" to it.transactionId?.let { id -> t.get(id)?.uuid },
                    "note" to it.note, "createdAt" to it.createdAt, "updatedAt" to it.createdAt,
                )
            }
            PEOPLE -> l.personByUuid(uuid)?.let {
                mapOf(
                    "name" to it.name, "phone" to it.phone, "note" to it.note,
                    "createdAt" to it.createdAt, "updatedAt" to it.updatedAt,
                )
            }
            LOANS -> l.loanWithStatsByUuid(uuid)?.loan?.let {
                mapOf(
                    "personUuid" to l.getPerson(it.personId)?.uuid, "direction" to it.direction.name,
                    "principal" to it.principal, "annualRate" to it.annualRate, "startDate" to it.startDate,
                    "dueDate" to it.dueDate, "accountUuid" to accU(it.accountId),
                    "transactionUuid" to it.transactionId?.let { id -> t.get(id)?.uuid },
                    "note" to it.note, "status" to it.status.name,
                    "createdAt" to it.createdAt, "updatedAt" to it.updatedAt,
                )
            }
            LOAN_ENTRIES -> l.entryByUuid(uuid)?.let {
                mapOf(
                    "loanUuid" to l.getLoan(it.loanId)?.uuid, "kind" to it.kind.name, "amount" to it.amount,
                    "date" to it.date, "accountUuid" to accU(it.accountId),
                    "transactionUuid" to it.transactionId?.let { id -> t.get(id)?.uuid },
                    "note" to it.note, "createdAt" to it.createdAt, "updatedAt" to it.createdAt,
                )
            }
            else -> null
        }
    }

    /** All local uuids of a collection (for "what does the cloud not have yet"). */
    suspend fun localRows(db: KoshDatabase, collection: String): List<Pair<String, Long>> = when (collection) {
        ACCOUNTS -> db.accountDao().all().map { it.uuid to it.updatedAt }
        CATEGORIES -> db.categoryDao().all().map { it.uuid to it.updatedAt }
        TRANSACTIONS -> db.txnDao().all().map { it.uuid to it.updatedAt }
        EMIS -> db.emiDao().all().map { it.uuid to it.updatedAt }
        EMI_PAYMENTS -> db.emiDao().allPayments().map { it.uuid to it.createdAt }
        PEOPLE -> db.loanDao().allPeople().map { it.uuid to it.updatedAt }
        LOANS -> db.loanDao().allLoans().map { it.uuid to it.updatedAt }
        LOAN_ENTRIES -> db.loanDao().allEntries().map { it.uuid to it.createdAt }
        else -> emptyList()
    }

    // ------------------------------------------------------------------ download

    enum class ApplyResult { APPLIED, LOCAL_NEWER, SKIPPED }

    /**
     * Applies one cloud document to the local database (insert, update or delete).
     * Runs inside the caller's DB transaction; does NOT queue anything for upload.
     * [cloudUuids] = all uuids the cloud holds for this collection (used to adopt the
     * default categories/accounts a fresh install seeded, instead of duplicating them).
     */
    suspend fun apply(
        db: KoshDatabase,
        collection: String,
        uuid: String,
        doc: Map<String, Any?>,
        cloudUuids: Set<String>,
    ): ApplyResult {
        val a = db.accountDao(); val c = db.categoryDao(); val t = db.txnDao(); val e = db.emiDao(); val l = db.loanDao()
        val deleted = doc.bool("deleted")
        val ts = updatedAt(doc)
        suspend fun accId(u: String?) = u?.let { a.byUuid(it)?.id }
        suspend fun catId(u: String?) = u?.let { c.byUuid(it)?.id }
        suspend fun txnId(u: String?) = u?.let { t.byUuid(it)?.id }

        when (collection) {
            ACCOUNTS -> {
                var local = a.byUuid(uuid)
                if (deleted) { local?.let { a.delete(it) }; return ApplyResult.APPLIED }
                if (local == null) {
                    // Adopt a same-named seeded account that the cloud doesn't know about.
                    local = a.all().firstOrNull {
                        it.name.equals(doc.str("name"), true) && it.type.name == doc.str("type") && it.uuid !in cloudUuids
                    }
                }
                if (local != null && local.uuid == uuid && local.updatedAt > ts) return ApplyResult.LOCAL_NEWER
                val row = Account(
                    id = local?.id ?: 0, uuid = uuid, name = doc.str("name"),
                    type = doc.enum("type", AccountType.BANK), openingBalance = doc.long("openingBalance"),
                    institution = doc.str("institution"), last4 = doc.str("last4"), color = doc.long("color").toInt(),
                    includeInTotal = doc.bool("includeInTotal", true), archived = doc.bool("archived"),
                    sortOrder = doc.int("sortOrder"), createdAt = doc.long("createdAt"), updatedAt = ts,
                )
                if (local == null) a.insert(row) else a.update(row)
            }
            CATEGORIES -> {
                var local = c.byUuid(uuid)
                if (deleted) { local?.let { c.delete(it) }; return ApplyResult.APPLIED }
                val kind = doc.enum("kind", CategoryKind.EXPENSE)
                if (local == null) {
                    local = c.findByName(doc.str("name"), kind)?.takeIf { it.uuid !in cloudUuids }
                }
                if (local != null && local.uuid == uuid && local.updatedAt > ts) return ApplyResult.LOCAL_NEWER
                val row = Category(
                    id = local?.id ?: 0, uuid = uuid, name = doc.str("name"), kind = kind,
                    emoji = doc.str("emoji").ifEmpty { "🏷️" }, color = doc.long("color").toInt(),
                    monthlyBudget = doc.long("monthlyBudget"), archived = doc.bool("archived"),
                    sortOrder = doc.int("sortOrder"), createdAt = doc.long("createdAt"), updatedAt = ts,
                )
                if (local == null) c.insert(row) else c.update(row)
            }
            PEOPLE -> {
                val local = l.personByUuid(uuid)
                if (deleted) { local?.let { l.deletePerson(it) }; return ApplyResult.APPLIED }
                if (local != null && local.updatedAt > ts) return ApplyResult.LOCAL_NEWER
                val row = Person(
                    id = local?.id ?: 0, uuid = uuid, name = doc.str("name"), phone = doc.str("phone"),
                    note = doc.str("note"), createdAt = doc.long("createdAt"), updatedAt = ts,
                )
                if (local == null) l.insertPerson(row) else l.updatePerson(row)
            }
            EMIS -> {
                val local = e.byUuid(uuid)
                if (deleted) { local?.let { e.delete(it) }; return ApplyResult.APPLIED }
                if (local != null && local.updatedAt > ts) return ApplyResult.LOCAL_NEWER
                val row = Emi(
                    id = local?.id ?: 0, uuid = uuid, name = doc.str("name"), lender = doc.str("lender"),
                    principal = doc.long("principal"), annualRate = doc.dbl("annualRate"),
                    tenureMonths = doc.int("tenureMonths"), emiAmount = doc.long("emiAmount"),
                    firstDueDate = doc.long("firstDueDate"), accountId = accId(doc.strOrNull("accountUuid")),
                    categoryId = catId(doc.strOrNull("categoryUuid")), paidBeforeTracking = doc.int("paidBeforeTracking"),
                    autoPay = doc.bool("autoPay"), remindDaysBefore = doc.int("remindDaysBefore"),
                    note = doc.str("note"), status = doc.enum("status", EmiStatus.ACTIVE),
                    createdAt = doc.long("createdAt"), updatedAt = ts,
                )
                if (local == null) e.insert(row) else e.update(row)
            }
            LOANS -> {
                val local = l.loanWithStatsByUuid(uuid)?.loan
                if (deleted) { local?.let { l.deleteLoan(it) }; return ApplyResult.APPLIED }
                if (local != null && local.updatedAt > ts) return ApplyResult.LOCAL_NEWER
                val personId = doc.strOrNull("personUuid")?.let { l.personByUuid(it)?.id } ?: return ApplyResult.SKIPPED
                val row = Loan(
                    id = local?.id ?: 0, uuid = uuid, personId = personId,
                    direction = doc.enum("direction", LoanDirection.LENT), principal = doc.long("principal"),
                    annualRate = doc.dbl("annualRate"), startDate = doc.long("startDate"), dueDate = doc.longOrNull("dueDate"),
                    accountId = accId(doc.strOrNull("accountUuid")),
                    // Linked transaction is resolved in a second pass (transactions are applied after loans).
                    transactionId = local?.transactionId,
                    note = doc.str("note"), status = doc.enum("status", LoanStatus.OPEN),
                    createdAt = doc.long("createdAt"), updatedAt = ts,
                )
                if (local == null) l.insertLoan(row) else l.updateLoan(row)
            }
            TRANSACTIONS -> {
                val local = t.byUuid(uuid)
                if (deleted) { local?.let { t.delete(it) }; return ApplyResult.APPLIED }
                if (local != null && local.updatedAt > ts) return ApplyResult.LOCAL_NEWER
                val accountId = accId(doc.strOrNull("accountUuid")) ?: return ApplyResult.SKIPPED
                val row = Txn(
                    id = local?.id ?: 0, uuid = uuid, type = doc.enum("type", TxType.EXPENSE), amount = doc.long("amount"),
                    accountId = accountId, toAccountId = accId(doc.strOrNull("toAccountUuid")),
                    categoryId = catId(doc.strOrNull("categoryUuid")), payee = doc.str("payee"), note = doc.str("note"),
                    date = doc.long("date"), paymentMode = doc.enum("paymentMode", PaymentMode.UPI),
                    emiId = doc.strOrNull("emiUuid")?.let { e.byUuid(it)?.id },
                    loanId = doc.strOrNull("loanUuid")?.let { l.loanWithStatsByUuid(it)?.loan?.id },
                    createdAt = doc.long("createdAt"), updatedAt = ts,
                )
                if (local == null) t.insert(row) else t.update(row)
            }
            EMI_PAYMENTS -> {
                val local = e.paymentByUuid(uuid)
                if (deleted) { local?.let { e.deletePayment(it) }; return ApplyResult.APPLIED }
                val emiId = doc.strOrNull("emiUuid")?.let { e.byUuid(it)?.id } ?: return ApplyResult.SKIPPED
                val n = doc.int("installmentNo")
                // Respect the (emiId, installmentNo) unique index.
                val clash = e.payment(emiId, n)
                if (clash != null && clash.uuid != uuid) return ApplyResult.SKIPPED
                val row = EmiPayment(
                    id = local?.id ?: 0, uuid = uuid, emiId = emiId, installmentNo = n,
                    dueDate = doc.long("dueDate"), paidDate = doc.long("paidDate"), amount = doc.long("amount"),
                    transactionId = txnId(doc.strOrNull("transactionUuid")), note = doc.str("note"),
                    createdAt = doc.long("createdAt"),
                )
                if (local == null) e.insertPayment(row) else e.updatePayment(row)
            }
            LOAN_ENTRIES -> {
                val local = l.entryByUuid(uuid)
                if (deleted) { local?.let { l.deleteEntry(it) }; return ApplyResult.APPLIED }
                val loanId = doc.strOrNull("loanUuid")?.let { l.loanWithStatsByUuid(it)?.loan?.id } ?: return ApplyResult.SKIPPED
                val row = LoanEntry(
                    id = local?.id ?: 0, uuid = uuid, loanId = loanId, kind = doc.enum("kind", LoanEntryKind.REPAYMENT),
                    amount = doc.long("amount"), date = doc.long("date"), accountId = accId(doc.strOrNull("accountUuid")),
                    transactionId = txnId(doc.strOrNull("transactionUuid")), note = doc.str("note"),
                    createdAt = doc.long("createdAt"),
                )
                if (local == null) l.insertEntry(row) else l.updateEntry(row)
            }
            else -> return ApplyResult.SKIPPED
        }
        return ApplyResult.APPLIED
    }

    /** Second pass after transactions exist: re-link a loan to its opening transaction. */
    suspend fun relinkLoan(db: KoshDatabase, uuid: String, doc: Map<String, Any?>) {
        val l = db.loanDao()
        val loan = l.loanWithStatsByUuid(uuid)?.loan ?: return
        val tid = doc.strOrNull("transactionUuid")?.let { db.txnDao().byUuid(it)?.id }
        if (tid != loan.transactionId) l.updateLoan(loan.copy(transactionId = tid))
    }
}
