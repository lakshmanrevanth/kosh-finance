package com.neuralroots.kosh.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Money helpers. Amounts are Long minor units (paise / cents). */
object Money {
    @Volatile var currencyCode: String = "INR"
        set(value) { field = value; cache.clear() }

    private val cache = HashMap<String, NumberFormat>()

    private fun locale(): Locale = if (currencyCode == "INR") Locale("en", "IN") else Locale.getDefault()

    val symbol: String
        get() = runCatching { Currency.getInstance(currencyCode).getSymbol(locale()) }.getOrDefault(currencyCode)

    private fun formatter(decimals: Boolean): NumberFormat = synchronized(cache) {
        cache.getOrPut("$currencyCode-$decimals") {
            NumberFormat.getCurrencyInstance(locale()).apply {
                runCatching { currency = Currency.getInstance(currencyCode) }
                minimumFractionDigits = if (decimals) 2 else 0
                maximumFractionDigits = if (decimals) 2 else 0
            }
        }
    }

    /** ₹1,23,456.50 — drops ".00" when [smart] is true and amount is whole. */
    fun format(minor: Long, smart: Boolean = true): String {
        val whole = minor % 100 == 0L
        val f = formatter(decimals = !(smart && whole))
        return f.format(BigDecimal(minor).movePointLeft(2))
    }

    /** +₹500 / −₹500 */
    fun formatSigned(minor: Long): String = when {
        minor > 0 -> "+" + format(minor)
        minor < 0 -> "−" + format(-minor)
        else -> format(0)
    }

    /** Compact for charts / widgets: ₹1.2L, ₹45K, ₹2.3Cr (INR) or 1.2M style otherwise. */
    fun compact(minor: Long): String {
        val neg = minor < 0
        val v = kotlin.math.abs(minor) / 100.0
        val s = if (currencyCode == "INR") {
            when {
                v >= 1_00_00_000 -> trim(v / 1_00_00_000) + "Cr"
                v >= 1_00_000 -> trim(v / 1_00_000) + "L"
                v >= 1_000 -> trim(v / 1_000) + "K"
                else -> trim(v)
            }
        } else {
            when {
                v >= 1_000_000_000 -> trim(v / 1_000_000_000) + "B"
                v >= 1_000_000 -> trim(v / 1_000_000) + "M"
                v >= 1_000 -> trim(v / 1_000) + "K"
                else -> trim(v)
            }
        }
        return (if (neg) "−" else "") + symbol + s
    }

    private fun trim(d: Double): String = DecimalFormat("#.#", DecimalFormatSymbols(Locale.US)).format(d)

    /** Parses user input like "1,234.5" into minor units. Returns null if invalid or empty. */
    fun parse(input: String): Long? {
        val clean = input.replace(",", "").replace(symbol, "").trim()
        if (clean.isEmpty()) return null
        return runCatching {
            BigDecimal(clean).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
        }.getOrNull()
    }

    /** Minor units to plain editable text: 123450 -> "1234.5", 100000 -> "1000". */
    fun toInput(minor: Long): String =
        BigDecimal(minor).movePointLeft(2).stripTrailingZeros().toPlainString()

    /** Minor units to a Double in major units (for spreadsheets). */
    fun toMajor(minor: Long): Double = minor / 100.0

    val supportedCurrencies = listOf("INR", "USD", "EUR", "GBP", "AED", "SGD", "AUD", "CAD", "JPY")
}
