package com.mj.money.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

fun Long.formatAmount(): String = "%,d".format(Locale.KOREA, this)

fun Long.formatWon(): String = "${formatAmount()}원"

/**
 * 차트 라벨용 짧은 금액: 9,800 / 12.8만 / 45만 / 128만.
 * 만 원 미만은 그대로, 백만 원 미만은 소수 한 자리(0이면 생략), 그 이상은 정수 만.
 */
fun Long.formatCompact(): String {
    val abs = kotlin.math.abs(this)
    val sign = if (this < 0) "-" else ""
    return when {
        abs < 10_000 -> sign + abs.formatAmount()
        abs < 1_000_000 -> {
            val tenths = abs / 1_000 // 0.1만 단위
            val text = if (tenths % 10 == 0L) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
            "$sign${text}만"
        }
        else -> "$sign${(abs / 10_000).formatAmount()}만"
    }
}

fun Long.toLocalDateTime(zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(this), zone)

fun Long.toLocalDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    toLocalDateTime(zone).toLocalDate()

fun LocalDateTime.toEpochMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
    atZone(zone).toInstant().toEpochMilli()

private fun LocalDate.weekday(): String = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)

/** 25일 (금) */
fun LocalDate.dayLabel(): String = "${dayOfMonth}일 (${weekday()})"

/** 9월 25일 (금) */
fun LocalDate.fullDayLabel(): String = "${monthValue}월 ${dayOfMonth}일 (${weekday()})"

/** 9.25 */
fun LocalDate.shortLabel(): String = "$monthValue.$dayOfMonth"

/** 10월 (올해가 아니면 2025년 12월) */
fun BudgetPeriod.titleText(today: LocalDate = LocalDate.now()): String =
    if (label.year == today.year) "${label.monthValue}월" else "${label.year}년 ${label.monthValue}월"

/** 9.25 ~ 10.24 */
fun BudgetPeriod.rangeText(): String = "${start.shortLabel()} ~ ${endInclusive.shortLabel()}"
