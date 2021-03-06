// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.shared.domain

import fi.espoo.evaka.shared.db.getUUID
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.kotlin.mapTo
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.temporal.ChronoUnit
import java.util.UUID

fun orMax(date: LocalDate?): LocalDate = date ?: LocalDate.MAX

fun minEndDate(first: LocalDate?, second: LocalDate?): LocalDate? {
    return when {
        first == null -> second
        second == null -> first
        else -> minOf(first, second)
    }
}

fun maxEndDate(first: LocalDate?, second: LocalDate?): LocalDate? {
    return when {
        first == null || second == null -> null
        else -> maxOf(first, second)
    }
}

data class ClosedPeriod(val start: LocalDate, val end: LocalDate) {
    init {
        require(start <= orMax(end)) { "Attempting to initialize invalid period with start: $start, end: $end" }
    }

    fun contains(value: ClosedPeriod) = this.start <= value.start && value.end <= this.end
    fun includes(value: LocalDate) = this.start <= value && value <= this.end
    fun overlaps(value: ClosedPeriod) = this.start <= value.end && value.start <= this.end

    fun adjacentTo(other: ClosedPeriod) = other.end.plusDays(1) == this.start || this.end.plusDays(1) == other.start

    fun intersection(value: ClosedPeriod): ClosedPeriod? {
        val start = maxOf(this.start, value.start)
        val end = minOf(this.end, value.end)
        return if (start <= end) ClosedPeriod(start, end) else null
    }

    fun dates(): Sequence<LocalDate> = generateSequence(start) { if (it < end) it.plusDays(1) else null }
    fun durationInDays(): Long = ChronoUnit.DAYS.between(start, end.plusDays(1)) // adjust to exclusive range
}

data class Period(val start: LocalDate, val end: LocalDate?) {
    init {
        check(start <= orMax(end)) { "Attempting to initialize invalid period with start: $start, end: $end" }
    }

    fun contains(value: Period) = this.start <= value.start && orMax(value.end) <= orMax(this.end)
    fun includes(value: LocalDate) = this.start <= value && value <= orMax(this.end)
    fun overlaps(value: Period) = this.start <= orMax(value.end) && value.start <= orMax(this.end)
}

private fun periodsCanMerge(first: Period, second: Period): Boolean =
    first.overlaps(second) || first.end?.let { first.end.plusDays(1) == second.start } ?: false

private fun minimalCover(first: Period, second: Period): Period = Period(
    minOf(first.start, second.start),
    if (first.end == null || second.end == null) null else maxOf(first.end, second.end)
)

private fun <T> simpleEquals(a: T, b: T): Boolean = a == b

fun <T> mergePeriods(values: List<Pair<Period, T>>, equals: (T, T) -> Boolean = ::simpleEquals): List<Pair<Period, T>> {
    return values.sortedBy { (period, _) -> period.start }
        .fold(listOf()) { periods, (period, value) ->
            when {
                periods.isEmpty() -> listOf(period to value)
                else ->
                    periods.last().let { (lastPeriod, lastValue) ->
                        when {
                            equals(lastValue, value) && periodsCanMerge(lastPeriod, period) ->
                                periods.dropLast(1) + (minimalCover(lastPeriod, period) to value)
                            else -> periods + (period to value)
                        }
                    }
            }
        }
}

fun asDistinctPeriods(periods: List<Period>, spanningPeriod: Period): List<Period> {
    // Includes the end dates with one day added to fill in gaps in original periods
    val allStartDates = (periods.flatMap { listOf(it.start, it.end?.plusDays(1)) } + spanningPeriod.start)
        .asSequence()
        .filterNotNull()
        .filter { spanningPeriod.start <= it && it <= orMax(spanningPeriod.end) }
        .distinct()
        .sorted()

    // Includes the start dates with one day subtracted to fill in gaps in original periods
    val allEndDates = (periods.flatMap { listOf(it.end, it.start.minusDays(1)) } + spanningPeriod.end)
        .filter { spanningPeriod.start <= orMax(it) && orMax(it) <= orMax(spanningPeriod.end) }
        .distinct()
        .sortedBy { orMax(it) }

    return allStartDates.map { start -> Period(start, allEndDates.find { end -> start <= orMax(end) }) }.toList()
}

fun operationalDays(h: Handle, year: Int, month: Month): Map<UUID, List<LocalDate>> {
    val firstDayOfMonth = LocalDate.of(year, month, 1)
    val days = generateSequence(firstDayOfMonth) { it.plusDays(1) }
        .takeWhile { date -> date.month == month }

    val unitOperationalDays = h.createQuery("SELECT id, operation_days FROM daycare")
        .map { rs, _ -> rs.getUUID("id") to rs.getArray("operation_days").array as Array<*> }
        .map { (id, days) -> id to days.map { it as Int }.map { DayOfWeek.of(it) } }
        .toList()

    val holidays = h.createQuery("SELECT date FROM holiday WHERE daterange(:start, :end, '[]') @> date")
        .bind("start", firstDayOfMonth)
        .bind("end", firstDayOfMonth.plusMonths(1))
        .mapTo<LocalDate>()
        .list()

    return unitOperationalDays
        .map { (unitId, operationalDays) ->
            val operationalDates = days
                .filter { operationalDays.contains(it.dayOfWeek) }
                // units that are operational every day of the week are also operational during holidays
                .filter { operationalDays.size == 7 || !holidays.contains(it) }

            unitId to operationalDates.toList()
        }
        .toMap()
}

val isWeekday = { date: LocalDate -> date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY }

fun LocalDate.isWeekend() = this.dayOfWeek == DayOfWeek.SATURDAY || this.dayOfWeek == DayOfWeek.SUNDAY
fun LocalDate.toClosedPeriod(): ClosedPeriod = ClosedPeriod(this, this)
