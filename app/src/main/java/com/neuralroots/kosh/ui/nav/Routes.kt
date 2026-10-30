package com.neuralroots.kosh.ui.nav

import com.neuralroots.kosh.data.db.CategoryKind
import com.neuralroots.kosh.data.db.TxType

/**
 * All navigation routes. Optional Long args use -1 for "none".
 * Always navigate with the builder functions, e.g. `nav.navigate(Routes.txnEdit(type = TxType.INCOME))`.
 */
object Routes {
    // Bottom-bar destinations
    const val HOME = "home"
    const val TRANSACTIONS = "transactions"
    const val EMIS = "emis"
    const val LOANS = "loans"
    const val MORE = "more"

    const val TXN_EDIT = "txn/edit?id={id}&type={type}&categoryId={categoryId}&accountId={accountId}"
    fun txnEdit(id: Long? = null, type: TxType? = null, categoryId: Long? = null, accountId: Long? = null) =
        "txn/edit?id=${id ?: -1}&type=${type?.name ?: "EXPENSE"}&categoryId=${categoryId ?: -1}&accountId=${accountId ?: -1}"

    const val ACCOUNTS = "accounts"
    const val ACCOUNT_EDIT = "account/edit?id={id}"
    fun accountEdit(id: Long? = null) = "account/edit?id=${id ?: -1}"
    const val ACCOUNT_DETAIL = "account/{id}"
    fun accountDetail(id: Long) = "account/$id"

    const val CATEGORIES = "categories"
    const val CATEGORY_EDIT = "category/edit?id={id}&kind={kind}"
    fun categoryEdit(id: Long? = null, kind: CategoryKind = CategoryKind.EXPENSE) = "category/edit?id=${id ?: -1}&kind=${kind.name}"

    const val EMI_EDIT = "emi/edit?id={id}"
    fun emiEdit(id: Long? = null) = "emi/edit?id=${id ?: -1}"
    const val EMI_DETAIL = "emi/{id}"
    fun emiDetail(id: Long) = "emi/$id"

    const val LOAN_EDIT = "loan/edit?id={id}&personId={personId}"
    fun loanEdit(id: Long? = null, personId: Long? = null) = "loan/edit?id=${id ?: -1}&personId=${personId ?: -1}"
    const val LOAN_DETAIL = "loan/{id}"
    fun loanDetail(id: Long) = "loan/$id"
    const val PERSON_DETAIL = "person/{id}"
    fun personDetail(id: Long) = "person/$id"
    const val PERSON_EDIT = "person/edit?id={id}"
    fun personEdit(id: Long? = null) = "person/edit?id=${id ?: -1}"

    const val REPORTS = "reports"
    const val SETTINGS = "settings"
    const val SHEETS_SYNC = "settings/sheets"
    const val EXPORT = "settings/export"
    const val PROFILE = "profile"

    val topLevel = setOf(HOME, TRANSACTIONS, EMIS, LOANS, MORE)
}
