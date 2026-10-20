package com.mj.money.domain

import com.mj.money.data.db.ExpensePoint
import com.mj.money.data.db.TransactionRow
import com.mj.money.data.db.TxSource
import com.mj.money.data.db.TxStatus
import com.mj.money.data.db.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class StatsTest {

    private val periods = PeriodCalculator(startDay = 25)
    private val october = YearMonth.of(2026, 10) // 9.25 ~ 10.24
    private val september = YearMonth.of(2026, 9) // 8.25 ~ 9.24

    private fun point(date: LocalDate, amount: Long, categoryId: Long?) =
        ExpensePoint(date.atTime(LocalTime.NOON).toEpochMillis(), amount, categoryId)

    private fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)

    private val points = listOf(
        point(d(9, 26), 10_000, 1), // 10월 식비
        point(d(10, 3), 5_000, 1), // 10월 식비
        point(d(10, 24), 120_000, 9), // 10월 구독
        point(d(9, 1), 8_000, 1), // 9월 식비
        point(d(8, 25), 25_000, 9), // 9월 구독
        point(d(9, 24), 3_000, null), // 9월 미분류
    )

    @Test
    fun `카테고리별 합계는 큰 순서이고 지난달 금액을 함께 준다`() {
        val shares = categoryShares(points, periods, october)
        assertEquals(listOf(9L, 1L), shares.map { it.categoryId })
        assertEquals(120_000, shares[0].total)
        assertEquals(25_000, shares[0].previous)
        assertEquals(15_000, shares[1].total)
        assertEquals(2, shares[1].count)
        assertEquals(8_000, shares[1].previous)
    }

    @Test
    fun `월별 합계는 25일 기준 달로 묶고 없는 달은 0`() {
        val totals = monthlyTotals(points, periods, listOf(YearMonth.of(2026, 8), september, october))
        assertEquals(listOf(0L, 36_000L, 135_000L), totals.map { it.second })
    }

    @Test
    fun `진행 중인 달은 지난달도 같은 날수까지만 비교한다`() {
        // 10월 3일 = 10월(9.25~)의 9일째 → 9월도 8.25 ~ 9.2까지만
        val (amount, partial) = previousComparable(points, periods, october, today = d(10, 3))
        assertTrue(partial)
        assertEquals(33_000, amount) // 8.25 구독 25,000 + 9.1 식비 8,000 (9.24 미분류는 빠짐)
    }

    @Test
    fun `지난 달을 볼 때는 그 전달 전체와 비교한다`() {
        val (amount, partial) = previousComparable(points, periods, october, today = d(11, 5))
        assertFalse(partial)
        assertEquals(36_000, amount)
    }

    @Test
    fun `도넛은 큰 카테고리 5개와 그 외로 6조각을 넘지 않는다`() {
        val shares = (1L..8L).map { CategoryShare(it, total = 100L * (9 - it), previous = 0, count = 1) }
        val groups = donutGroups(shares, isAlwaysOther = { false })
        assertEquals(6, groups.size)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), groups.take(5).map { it.categoryIds.single() })
        assertTrue(groups.last().isOther)
        assertEquals(300 + 200 + 100L, groups.last().total)
    }

    @Test
    fun `기타와 미분류는 크기와 상관없이 그 외에 들어간다`() {
        val shares = listOf(
            CategoryShare(12, 500, 0, 1), // 기타
            CategoryShare(1, 300, 0, 1),
            CategoryShare(null, 100, 0, 1),
        )
        val groups = donutGroups(shares, isAlwaysOther = { it == null || it == 12L })
        assertEquals(listOf(listOf<Long?>(1L)), groups.filterNot { it.isOther }.map { it.categoryIds })
        assertEquals(listOf(12L, null), groups.single { it.isOther }.categoryIds)
    }

    @Test
    fun `남은 카테고리가 하나뿐이면 그 외로 묶지 않는다`() {
        val shares = (1L..6L).map { CategoryShare(it, total = 100L * (7 - it), previous = 0, count = 1) }
        val groups = donutGroups(shares, isAlwaysOther = { false })
        assertEquals(6, groups.size)
        assertTrue(groups.none { it.isOther })
    }

    @Test
    fun `히트맵 단계 - 하루 예산 기준`() {
        val scale = heatScale(dailyBudget = 20_000, maxDaily = 0)
        assertEquals(0, scale.level(0))
        assertEquals(1, scale.level(5_000)) // 절반 이하
        assertEquals(2, scale.level(20_000)) // 하루 예산 이하
        assertEquals(3, scale.level(30_000)) // 2배 이하
        assertEquals(4, scale.level(100_000))
    }

    @Test
    fun `히트맵 단계 - 예산이 없으면 가장 많이 쓴 날 기준`() {
        val scale = heatScale(dailyBudget = 0, maxDaily = 100_000)
        assertEquals(1, scale.level(20_000))
        assertEquals(4, scale.level(100_000))
    }

    @Test
    fun `많이 쓴 곳은 가맹점, 없으면 메모로 묶는다`() {
        fun row(id: Long, amount: Long, merchant: String?, memo: String?) = TransactionRow(
            id, TxType.EXPENSE, amount, 0, merchant, memo, TxStatus.CONFIRMED, TxSource.MANUAL,
            1, "식비", "🍚", null, null, null, null,
        )
        val top = topMerchants(
            listOf(
                row(1, 23_500, "쿠팡이츠", null),
                row(2, 18_000, "쿠팡이츠", null),
                row(3, 43_200, null, "이마트"),
                row(4, 8_000, null, null),
                row(5, 3_000, null, " "),
            )
        )
        assertEquals(listOf("이마트", "쿠팡이츠", NO_MEMO), top.map { it.name })
        assertEquals(2, top[1].count)
        assertEquals(11_000, top[2].total)
    }

    @Test
    fun `결제수단별 합계는 가계부 달 기준`() {
        val pointsByCard = listOf(
            ExpensePoint(d(9, 26).atTime(LocalTime.NOON).toEpochMillis(), 10_000, 1, paymentMethodId = 1),
            ExpensePoint(d(10, 20).atTime(LocalTime.NOON).toEpochMillis(), 30_000, 1, paymentMethodId = 1),
            ExpensePoint(d(10, 1).atTime(LocalTime.NOON).toEpochMillis(), 5_000, 1, paymentMethodId = 4),
            ExpensePoint(d(9, 24).atTime(LocalTime.NOON).toEpochMillis(), 99_000, 1, paymentMethodId = 1), // 9월
        )
        val shares = paymentShares(pointsByCard, periods, october)
        assertEquals(listOf(1L to 40_000L, 4L to 5_000L), shares.map { it.paymentMethodId to it.total })
    }

    @Test
    fun `카드 실적은 달력 한 달 기준 - 진행 중인 달은 오늘이 속한 달`() {
        val period = periods.periodFor(october) // 9.25 ~ 10.24
        assertEquals(YearMonth.of(2026, 9), cardMonthFor(period, today = d(9, 26)))
        assertEquals(YearMonth.of(2026, 10), cardMonthFor(period, today = d(10, 3)))
        assertEquals(YearMonth.of(2026, 10), cardMonthFor(period, today = d(11, 5))) // 지난 달은 끝나는 달
    }

    @Test
    fun `카드 실적 금액은 그 카드로 달력 한 달 동안 쓴 돈`() {
        val pointsByCard = listOf(
            ExpensePoint(d(9, 1).atTime(LocalTime.NOON).toEpochMillis(), 10_000, 1, paymentMethodId = 1),
            ExpensePoint(d(9, 30).atTime(LocalTime.NOON).toEpochMillis(), 20_000, 1, paymentMethodId = 1),
            ExpensePoint(d(10, 1).atTime(LocalTime.NOON).toEpochMillis(), 40_000, 1, paymentMethodId = 1),
            ExpensePoint(d(9, 15).atTime(LocalTime.NOON).toEpochMillis(), 7_000, 1, paymentMethodId = 2),
        )
        assertEquals(30_000, cardSpent(pointsByCard, paymentMethodId = 1, month = YearMonth.of(2026, 9)))
    }

    @Test
    fun `새 카테고리 색은 아직 안 쓴 첫 번호, 다 쓰면 회색`() {
        assertEquals(2, nextColorSlot(listOf(1, 3, 0)))
        assertEquals(0, nextColorSlot((1..8).toList()))
    }

    @Test
    fun `차트용 짧은 금액`() {
        assertEquals("9,800", 9_800L.formatCompact())
        assertEquals("1만", 10_000L.formatCompact())
        assertEquals("12.8만", 128_400L.formatCompact())
        assertEquals("32만", 320_000L.formatCompact())
        assertEquals("128만", 1_284_000L.formatCompact())
    }
}
