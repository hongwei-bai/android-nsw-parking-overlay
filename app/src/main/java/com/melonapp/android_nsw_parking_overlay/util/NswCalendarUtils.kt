package com.melonapp.android_nsw_parking_overlay.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.temporal.TemporalAdjusters

object NswCalendarUtils {

    fun isNswPublicHoliday(date: LocalDate): Boolean {
        val year = date.year
        val easterSunday = easterSunday(year)
        val holidays = buildSet {
            add(newYearHoliday(year))
            add(australiaDayHoliday(year))
            add(easterSunday.minusDays(2))
            add(easterSunday.minusDays(1))
            add(easterSunday)
            add(easterSunday.plusDays(1))
            add(LocalDate.of(year, Month.APRIL, 25))
            addAll(anzacAdditionalDays(year))
            add(kingsBirthday(year))
            add(labourDay(year))
            add(christmasDayHoliday(year))
            add(boxingDayHoliday(year))
        }
        return date in holidays
    }

    fun isFirstWeekOfMonth(date: LocalDate): Boolean = date.dayOfMonth <= 7

    fun isLastWeekOfMonth(date: LocalDate): Boolean =
        date.dayOfMonth > date.lengthOfMonth() - 7

    private fun newYearHoliday(year: Int): LocalDate {
        val newYear = LocalDate.of(year, Month.JANUARY, 1)
        return when (newYear.dayOfWeek) {
            DayOfWeek.SATURDAY -> newYear.plusDays(2)
            DayOfWeek.SUNDAY -> newYear.plusDays(1)
            else -> newYear
        }
    }

    private fun australiaDayHoliday(year: Int): LocalDate {
        val australiaDay = LocalDate.of(year, Month.JANUARY, 26)
        return when (australiaDay.dayOfWeek) {
            DayOfWeek.SATURDAY -> australiaDay.plusDays(2)
            DayOfWeek.SUNDAY -> australiaDay.plusDays(1)
            else -> australiaDay
        }
    }

    private fun anzacAdditionalDays(year: Int): Set<LocalDate> {
        return when (year) {
            2026 -> setOf(LocalDate.of(2026, Month.APRIL, 27))
            2027 -> setOf(LocalDate.of(2027, Month.APRIL, 26))
            else -> emptySet()
        }
    }

    private fun kingsBirthday(year: Int): LocalDate =
        LocalDate.of(year, Month.JUNE, 1)
            .with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.MONDAY))

    private fun labourDay(year: Int): LocalDate =
        LocalDate.of(year, Month.OCTOBER, 1)
            .with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY))

    private fun christmasDayHoliday(year: Int): LocalDate {
        val christmas = LocalDate.of(year, Month.DECEMBER, 25)
        return when (christmas.dayOfWeek) {
            DayOfWeek.SATURDAY -> christmas.plusDays(2)
            DayOfWeek.SUNDAY -> christmas.plusDays(2)
            else -> christmas
        }
    }

    private fun boxingDayHoliday(year: Int): LocalDate {
        val boxingDay = LocalDate.of(year, Month.DECEMBER, 26)
        return when (boxingDay.dayOfWeek) {
            DayOfWeek.SATURDAY -> boxingDay.plusDays(2)
            DayOfWeek.SUNDAY -> boxingDay.plusDays(2)
            else -> boxingDay
        }
    }

    private fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(year, month, day)
    }
}
