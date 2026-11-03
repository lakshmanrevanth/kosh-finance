package com.neuralroots.kosh.sms

import com.neuralroots.kosh.data.db.DetectedTxn
import com.neuralroots.kosh.data.db.PaymentMode
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Parses Indian bank / card / UPI / wallet transaction SMS into a [DetectedTxn].
 * Pure Kotlin (no Android APIs) so it is unit-testable on the JVM.
 *
 * Returns null for anything that isn't a completed money movement: OTPs, collect
 * requests, upcoming-debit reminders, failed transactions, promos, statements, etc.
 */
object SmsParser {

    // ------------------------------------------------------------------ sender

    private val shortCode = Regex("""^(?:[A-Z]{2}-)?([A-Z0-9]{3,9})(?:-[A-Z])?$""")
    private val senderKeywords = listOf(
        "BANK", "BK", "UPI", "PAYTM", "PHONEPE", "AMAZON", "APAY", "GPAY", "MOBIKWIK", "FREECHARGE",
        "SBI", "HDFC", "ICICI", "AXIS", "KOTAK", "YES", "IDFC", "INDUS", "BOB", "PNB", "CANARA", "UNION",
        "CARD", "CITI", "AMEX", "RBL", "FEDERAL", "AUBANK", "SLICE", "ONECARD", "JUPITER", "FI",
    )

    /** True for bank/merchant short codes like "AX-HDFCBK" / "VM-SBIUPI-S"; false for phone numbers. */
    fun isLikelyBankSender(sender: String): Boolean {
        val s = sender.trim().uppercase(Locale.ROOT)
        if (s.isEmpty()) return false
        val digits = s.count { it.isDigit() }
        if (s.startsWith("+") || digits >= 8) return false // personal / long numbers
        if (s.endsWith("-P")) return false // TRAI promotional suffix
        if (shortCode.matches(s)) return true
        return senderKeywords.any { s.contains(it) }
    }

    /** "AX-HDFCBK-S" -> "HDFCBK" */
    fun normalizeSender(sender: String): String {
        val s = sender.trim().uppercase(Locale.ROOT)
        return shortCode.matchEntire(s)?.groupValues?.get(1) ?: s.replace(Regex("[^A-Z0-9]"), "")
    }

    private val bankNames = listOf(
        "HDFC" to "HDFC", "SBI" to "SBI", "ICICI" to "ICICI", "AXIS" to "Axis", "KOTAK" to "Kotak",
        "PAYTM" to "Paytm", "PHONEPE" to "PhonePe", "YESB" to "Yes Bank", "IDFC" to "IDFC First",
        "INDUS" to "IndusInd", "BOB" to "Bank of Baroda", "BARODA" to "Bank of Baroda", "PNB" to "PNB",
        "CANARA" to "Canara", "CANBNK" to "Canara", "UNION" to "Union Bank", "FEDERAL" to "Federal",
        "FEDBNK" to "Federal", "CITI" to "Citi", "AMEX" to "Amex", "RBL" to "RBL", "AUBANK" to "AU Bank",
        "SCBANK" to "StanChart", "SLICE" to "Slice", "ONECARD" to "OneCard", "JUPITER" to "Jupiter",
        "AMAZON" to "Amazon Pay", "APAY" to "Amazon Pay", "MOBIKWIK" to "MobiKwik", "IOB" to "IOB",
        "BOI" to "Bank of India", "CBSSBI" to "SBI", "UCO" to "UCO", "INDBNK" to "Indian Bank",
    )

    /** Friendly bank/wallet name for a sender code, e.g. "VM-HDFCBK" -> "HDFC". */
    fun bankName(sender: String): String {
        val n = normalizeSender(sender)
        return bankNames.firstOrNull { n.contains(it.first) }?.second ?: n
    }

    // ------------------------------------------------------------------ filters

    private val rejectPhrases = listOf(
        // OTP / auth
        "otp", "one time password", "one-time password", "verification code", "security code",
        // collect requests & mandates set-up
        "has requested", "requested money", "request of rs", "collect request", "payment request",
        "mandate is created", "mandate created", "e-mandate registration", "approve the mandate",
        // future / reminders / bills
        "will be debited", "will be deducted", "will be auto-debited", "will be charged", "to be debited",
        "is due", "due on", "due date", "due by", "payment due", "minimum amount due", "min amt due",
        "total amount due", "total amt due", "statement for", "statement is", "e-statement", "bill of rs",
        "bill generated", "upcoming", "reminder", "scheduled for", "is scheduled", "pay now",
        // failures
        "failed", "declined", "unsuccessful", "could not be processed", "not processed", "has been rejected",
        "insufficient", "transaction not completed",
        // promotions
        "pre-approved", "preapproved", "pre approved", "loan approved", "apply now", "cashback up to",
        "cashback upto", "offer valid", "exclusive offer", "% off", "congratulations", "you have won",
        "lucky draw", "limit enhanced", "limit increase", "eligible for", "get up to", "get upto",
    )

    private val debitWords = listOf(
        "debited", "spent", "paid", "sent", "withdrawn", "withdrawal", "purchase", "txn of", "transaction of",
        "used for", "deducted", "debit of", "dr.", "transferred to", "payment of",
    )
    private val creditWords = listOf(
        "credited", "received", "deposited", "refund", "reversed", "reversal", "cashback of", "credit of", "cr.",
        "added to your",
    )

    // ------------------------------------------------------------------ extraction patterns

    private const val NUM = """([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
    private val amountAfterVerb = Regex(
        """(?:debited|credited|deducted|withdrawn|spent|paid|sent|received|deposited)\s+(?:by|with|for|of)?\s*(?:rs\.?|inr|₹)?\s*$NUM""",
        RegexOption.IGNORE_CASE,
    )
    private val currencyAmount = Regex("""(?:rs\.?|inr|₹)\s*$NUM""", RegexOption.IGNORE_CASE)
    private val balanceContext = Regex(
        """(?:bal|balance|avl|available|limit|lmt|outstanding)[^0-9]{0,18}$""",
        RegexOption.IGNORE_CASE,
    )

    private val accountPatterns = listOf(
        Regex(
            """\b(?:a/?c|acct|account|card|ac)\s*(?:no\.?|number|num)?\s*(?:ending\s*(?:with|in)?)?\s*[:.\-]?\s*[x*•.]*\s*([0-9]{3,6})\b""",
            RegexOption.IGNORE_CASE,
        ),
        Regex("""[x*•]{2,}\s*([0-9]{3,6})\b""", RegexOption.IGNORE_CASE),
        Regex("""ending\s*(?:with|in)?\s*([0-9]{3,6})\b""", RegexOption.IGNORE_CASE),
    )

    private val refPatterns = listOf(
        Regex("""(?:upi\s*ref(?:\.|erence)?\s*(?:no\.?|number|id)?|ref(?:erence)?\s*(?:no\.?|number|id)?|refno|utr(?:\s*no\.?)?|txn\s*(?:id|no\.?)|transaction\s*id|rrn)\s*[:#.\-]?\s*([A-Za-z0-9]{6,24})""", RegexOption.IGNORE_CASE),
        Regex("""\bupi[:/\-\s]+([0-9]{9,16})""", RegexOption.IGNORE_CASE),
    )

    private val vpa = Regex("""([A-Za-z0-9._\-]{2,64})@([A-Za-z][A-Za-z0-9]{1,20})""")

    // Merchant fragments end at these words/punctuation.
    private const val STOP =
        """(?=\s+(?:on|ref|refno|upi|via|for|avl|avbl|txn|dated|date|is|if|not|with|and|using|thru|through|by|at|from|to|bal|info|in|your|a/c|ac|has|have|was|been|credited|debited|under|updated)\b|\s*[.,;:()\n]|\s+\d|$)"""
    private const val NAME = """([A-Za-z][A-Za-z0-9&'_\-]*(?:\s+[A-Za-z0-9&'_\-]+){0,5}?)"""

    private val debitMerchantPatterns = listOf(
        Regex(""";\s*$NAME\s+credited""", RegexOption.IGNORE_CASE),
        Regex("""\bto\s+(?:vpa\s+)?([A-Za-z0-9._\-]+@[A-Za-z0-9]+)""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:trf|transfer(?:red)?|transferred)\s+to\s+$NAME$STOP""", RegexOption.IGNORE_CASE),
        Regex("""\bat\s+$NAME$STOP""", RegexOption.IGNORE_CASE),
        Regex("""\bto\s+$NAME$STOP""", RegexOption.IGNORE_CASE),
        Regex("""\btowards\s+$NAME$STOP""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:info|remarks?)\s*[:\-]\s*([^\n.]{2,60})""", RegexOption.IGNORE_CASE),
    )
    private val creditMerchantPatterns = listOf(
        Regex("""\bfrom\s+(?:vpa\s+)?([A-Za-z0-9._\-]+@[A-Za-z0-9]+)""", RegexOption.IGNORE_CASE),
        Regex("""\bfrom\s+$NAME$STOP""", RegexOption.IGNORE_CASE),
        Regex("""\bby\s+$NAME$STOP""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:info|remarks?)\s*[:\-]\s*([^\n.]{2,60})""", RegexOption.IGNORE_CASE),
    )

    /** Words that can follow "to/from/by/at" but are never a merchant. */
    private val notMerchant = setOf(
        "a", "an", "the", "a/c", "ac", "acct", "account", "your", "you", "yours", "card", "vpa", "upi", "neft",
        "imps", "rtgs", "transfer", "cash", "atm", "self", "bank", "beneficiary", "mobile", "number", "no",
        "net", "netbanking", "net banking", "linked", "debit", "credit", "ref", "it", "us", "call", "sms",
        "block", "dispute", "report", "rs", "inr", "xx", "this", "customer", "customer care", "branch",
        "nach", "ecs", "mandate", "si", "standing instruction", "ach", "salary", "pos", "ecom", "e-com",
        "wallet", "pay", "payment", "is", "on", "of",
    )

    // ------------------------------------------------------------------ main

    fun parse(sender: String, body: String, timestamp: Long): DetectedTxn? {
        if (!isLikelyBankSender(sender)) return null
        val text = body.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        val lower = text.lowercase(Locale.ROOT)
        if (rejectPhrases.any { lower.contains(it) }) return null

        val debitIdx = firstIndex(lower, debitWords)
        val creditIdx = firstIndex(lower, creditWords)
        if (debitIdx < 0 && creditIdx < 0) return null // balance-only / informational
        val isDebit = when {
            debitIdx < 0 -> false
            creditIdx < 0 -> true
            else -> debitIdx < creditIdx
        }

        val amount = extractAmount(text) ?: return null
        if (amount <= 0) return null

        val last4 = extractAccount(text)
        val ref = extractRef(text)
        val merchant = extractMerchant(text, isDebit)
        val mode = extractMode(lower, text)
        val fp = buildString {
            append(normalizeSender(sender)); append('|'); append(amount); append('|')
            append(if (isDebit) 'D' else 'C'); append('|')
            append(ref ?: (timestamp / 60_000L).toString())
        }
        return DetectedTxn(
            fingerprint = fp,
            sender = sender.trim(),
            body = body.trim(),
            amount = amount,
            isDebit = isDebit,
            merchant = merchant,
            accountLast4 = last4,
            refNo = ref ?: "",
            mode = mode,
            date = timestamp,
        )
    }

    private fun firstIndex(lower: String, words: List<String>): Int =
        words.mapNotNull { w ->
            // whole-word-ish match so "paid" doesn't hit inside "prepaid" etc.
            Regex("""(?<![a-z])${Regex.escape(w)}""").find(lower)?.range?.first
        }.minOrNull() ?: -1

    // ------------------------------------------------------------------ pieces

    /** Minor units of the transaction amount (ignores balance / limit amounts). */
    fun extractAmount(text: String): Long? {
        amountAfterVerb.find(text)?.let { m -> toMinor(m.groupValues[1])?.let { return it } }
        for (m in currencyAmount.findAll(text)) {
            val before = text.substring(0, m.range.first)
            if (balanceContext.containsMatchIn(before.takeLast(30))) continue
            toMinor(m.groupValues[1])?.let { return it }
        }
        return null
    }

    private fun toMinor(s: String): Long? = runCatching {
        BigDecimal(s.replace(",", "").trimEnd('.')).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
    }.getOrNull()

    fun extractAccount(text: String): String {
        for (p in accountPatterns) {
            val m = p.find(text) ?: continue
            val d = m.groupValues[1]
            return d.takeLast(4)
        }
        return ""
    }

    fun extractRef(text: String): String? {
        for (p in refPatterns) {
            for (m in p.findAll(text)) {
                val r = m.groupValues[1]
                if (r.any { it.isDigit() } && r.length >= 6) return r
            }
        }
        return null
    }

    fun extractMerchant(text: String, isDebit: Boolean): String {
        val patterns = if (isDebit) debitMerchantPatterns else creditMerchantPatterns
        for (p in patterns) {
            for (m in p.findAll(text)) {
                val raw = m.groupValues[1].trim()
                val cleaned = cleanMerchant(raw) ?: continue
                return cleaned
            }
        }
        // Fallback: any UPI id in the message that isn't the user's own handle.
        vpa.find(text)?.let { cleanVpa(it.value) }?.let { return it }
        return ""
    }

    private fun cleanMerchant(raw: String): String? {
        var s = raw.trim().trim('.', ',', ';', ':', '-', '(', ')', '\'', '"')
        if (s.contains('@')) return cleanVpa(s)
        // "UPI/P2M/430712345678/SWIGGY" or "UPI-430712345678-SWIGGY LIMITED"
        if (s.contains('/') || Regex("""^upi-""", RegexOption.IGNORE_CASE).containsMatchIn(s)) {
            val parts = s.split('/', '-').map { it.trim() }.filter { p -> p.isNotEmpty() && p.any { it.isLetter() } && !p.equals("upi", true) && !p.equals("p2m", true) && !p.equals("p2a", true) }
            s = parts.firstOrNull { it.count(Char::isLetter) >= 3 } ?: return null
        }
        s = s.replace(Regex("""\s+"""), " ").trim()
        if (s.length < 2) return null
        val low = s.lowercase(Locale.ROOT)
        if (low in notMerchant) return null
        val firstWord = low.substringBefore(' ')
        if (firstWord in setOf("a/c", "ac", "acct", "account", "your", "you", "card", "xx", "vpa", "the", "rs", "inr")) return null
        if (s.count { it.isDigit() } > s.count { it.isLetter() }) return null
        return titleCase(s)
    }

    /** "swiggy@icici" -> "Swiggy", "rahul.sharma99@okaxis" -> "Rahul Sharma". */
    fun cleanVpa(v: String): String? {
        val local = v.substringBefore('@')
        val junk = setOf("upi", "pay", "ok", "paytmqr", "bharatpe", "gpay")
        val tokens = local.split(Regex("""[._\-0-9]+""")).filter { it.length >= 2 && it.lowercase(Locale.ROOT) !in junk }
        val name = tokens.joinToString(" ").trim()
        if (name.isEmpty()) return null
        return titleCase(name)
    }

    private fun titleCase(s: String): String {
        // A single short all-caps code stays as is ("LIC", "IRCTC", "KFC").
        if (!s.contains(' ') && s.length <= 5 && s.all { it.isUpperCase() || !it.isLetter() }) return s
        return s.split(' ').joinToString(" ") { w ->
            w.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
    }

    fun extractMode(lower: String, text: String): PaymentMode = when {
        Regex("""\b(nach|ecs|mandate|auto[\s-]?debit|autopay|standing instruction|\bsi\b|ach)\b""").containsMatchIn(lower) -> PaymentMode.AUTO_DEBIT
        Regex("""\batm\b|cash withdrawal|withdrawn""").containsMatchIn(lower) -> PaymentMode.CASH
        Regex("""\bupi\b|vpa""").containsMatchIn(lower) || vpa.containsMatchIn(text) -> PaymentMode.UPI
        Regex("""credit card|debit card|\bcard\b|\bpos\b|spent on|spent at""").containsMatchIn(lower) -> PaymentMode.CARD
        Regex("""neft|imps|rtgs|net ?banking|\bib\b|internet banking""").containsMatchIn(lower) -> PaymentMode.NET_BANKING
        else -> PaymentMode.OTHER
    }
}
