package com.mj.money.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PeriodCalculatorTest {

    private val payday = PeriodCalculator(startDay = 25)

    @Test
    fun `25일부터 다음 달 24일까지가 끝나는 달 이름의 한 달이다`() {
        val period = payday.periodFor(YearMonth.of(2026, 10))

        assertEquals(LocalDate.of(2026, 9, 25), period.start)
        assertEquals(LocalDate.of(2026, 10, 24), period.endInclusive)
    }

    @Test
    fun `25일 당일은 새 달에 속한다`() {
        assertEquals(YearMonth.of(2026, 10), payday.periodOf(LocalDate.of(2026, 9, 25)).label)
    }

    @Test
    fun `24일은 아직 이전 달이다`() {
        assertEquals(YearMonth.of(2026, 9), payday.periodOf(LocalDate.of(2026, 9, 24)).label)
    }

    @Test
    fun `주말이어도 25일부터 새 달이다`() {
        // 2026-10-25는 일요일
        val sunday = LocalDate.of(2026, 10, 25)
        assertEquals(YearMonth.of(2026, 11), payday.periodOf(sunday).label)
        assertEquals(YearMonth.of(2026, 10), payday.periodOf(sunday.minusDays(2)).label)
    }

    @Test
    fun `연말은 12월 25일부터 다음 해 1월이 된다`() {
        val period = payday.periodOf(LocalDate.of(2026, 12, 31))

        assertEquals(YearMonth.of(2027, 1), period.label)
        assertEquals(LocalDate.of(2026, 12, 25), period.start)
        assertEquals(LocalDate.of(2027, 1, 24), period.endInclusive)
    }

    @Test
    fun `2월을 지나는 기간도 끊김 없이 이어진다`() {
        val march = payday.periodFor(YearMonth.of(2028, 3))
        val april = payday.periodFor(YearMonth.of(2028, 4))

        assertEquals(LocalDate.of(2028, 2, 25), march.start)
        assertEquals(march.endExclusive, april.start)
    }

    @Test
    fun `contains는 시작일을 포함하고 다음 시작일은 포함하지 않는다`() {
        val period = payday.periodFor(YearMonth.of(2026, 10))

        assertTrue(LocalDate.of(2026, 9, 25) in period)
        assertTrue(LocalDate.of(2026, 10, 24) in period)
        assertFalse(LocalDate.of(2026, 10, 25) in period)
        assertFalse(LocalDate.of(2026, 9, 24) in period)
    }

    @Test
    fun `시작일이 1일이면 달력의 한 달과 같다`() {
        val calendar = PeriodCalculator(startDay = 1)
        val period = calendar.periodOf(LocalDate.of(2026, 9, 25))

        assertEquals(YearMonth.of(2026, 9), period.label)
        assertEquals(LocalDate.of(2026, 9, 1), period.start)
        assertEquals(LocalDate.of(2026, 9, 30), period.endInclusive)
    }

    @Test
    fun `기간 표시 문자열`() {
        val period = payday.periodFor(YearMonth.of(2026, 10))

        assertEquals("9.25 ~ 10.24", period.rangeText())
        assertEquals("10월", period.titleText(today = LocalDate.of(2026, 9, 25)))
        assertEquals("2026년 10월", period.titleText(today = LocalDate.of(2027, 1, 1)))
    }
}
