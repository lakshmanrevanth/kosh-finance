package com.neuralroots.kosh.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object Dates {
    val zone: ZoneId get() = ZoneId.systemDefault()

    private val dayFmt = DateTimeFormatter.ofPattern("d MMM yyyy")
    private val dayShortFmt = DateTimeFormatter.ofPattern("d MMM")
    private val dayHeaderFmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
    private val timeFmt = DateTimeFormatter.ofPattern("h:mm a")
    private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy")
    private val monthShortFmt = DateTimeFormatter.ofPattern("MMM")
    private val isoFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val isoDayFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun toLocalDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    fun toLocalDateTime(ms: Long): LocalDateTime = Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime()
    fun toMs(d: LocalDate): Long = d.atStartOfDay(zone).toInstant().toEpochMilli()
    fun toMs(dt: LocalDateTime): Long = dt.atZone(zone).toInstant().toEpochMilli()

    fun startOfDay(ms: Long): Long = toMs(toLocalDate(ms))
    fun endOfDay(ms: Long): Long = toMs(toLocalDate(ms).plusDays(1)) - 1
    fun today(): LocalDate = LocalDate.now(zone)

    fun monthRange(ym: YearMonth): LongRange =
        toMs(ym.atDay(1))..(toMs(ym.plusMonths(1).atDay(1)) - 1)

    fun currentMonth(): YearMonth = YearMonth.now(zone)

    fun yearMonth(ms: Long): YearMonth = YearMonth.from(toLocalDate(ms))

    /** Same day-of-month [months] later, clamped to month length (31 Jan + 1 = 28/29 Feb). */
    fun plusMonths(ms: Long, months: Int, dayOfMonth: Int = toLocalDate(ms).dayOfMonth): Long {
        val base = toLocalDate(ms)
        val ym = YearMonth.from(base).plusMonths(months.toLong())
        val day = dayOfMonth.coerceAtMost(ym.lengthOfMonth())
        return toMs(ym.atDay(day))
    }

    fun daysBetween(fromMs: Long, toMs: Long): Long =
        ChronoUnit.DAYS.between(toLocalDate(fromMs), toLocalDate(toMs))

    fun daysFromToday(ms: Long): Long = ChronoUnit.DAYS.between(today(), toLocalDate(ms))

    fun formatDay(ms: Long): String = toLocalDate(ms).format(dayFmt)
    fun formatDayShort(ms: Long): String = toLocalDate(ms).format(dayShortFmt)
    fun formatTime(ms: Long): String = toLocalDateTime(ms).format(timeFmt)
    fun formatMonth(ym: YearMonth): String = ym.format(monthFmt)
    fun formatMonthShort(ym: YearMonth): String = ym.format(monthShortFmt)
    fun formatIso(ms: Long): String = toLocalDateTime(ms).format(isoFmt)
    fun formatIsoDay(ms: Long): String = toLocalDate(ms).format(isoDayFmt)

    /** "Today", "Yesterday", or "Mon, 3 Mar 2026". */
    fun dayHeader(ms: Long): String {
        val d = toLocalDate(ms)
        val t = today()
        return when (d) {
            t -> "Today"
            t.minusDays(1) -> "Yesterday"
            else -> d.format(dayHeaderFmt)
        }
    }

    /** "Due today", "Due in 3 days", "Overdue by 2 days". */
    fun relativeDue(ms: Long): String {
        val n = daysFromToday(ms)
        return when {
            n == 0L -> "Due today"
            n == 1L -> "Due tomorrow"
            n > 1 -> "Due in $n days"
            n == -1L -> "Overdue by 1 day"
            else -> "Overdue by ${-n} days"
        }
    }

    /** Keeps the date part of [dateMs] and the time part of [timeSource] (default now). */
    fun withCurrentTime(dateMs: Long, timeSource: Long = System.currentTimeMillis()): Long {
        val d = toLocalDate(dateMs)
        val t = toLocalDateTime(timeSource).toLocalTime()
        return toMs(d.atTime(t))
    }
}
