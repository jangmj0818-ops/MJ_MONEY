package com.mj.money.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** 금액은 모두 예시 값 */
class BudgetCalculatorTest {

    // 10월 = 9.25 ~ 10.24 (30일)
    private val october = PeriodCalculator(startDay = 25).periodFor(YearMonth.of(2026, 10))
    private val day10 = LocalDate.of(2026, 10, 4) // 9.25부터 10일째

    /** 월말에 나가는 큰 구독료 (아직 안 나감) */
    private val bigSubscription = FixedCharge(amount = 100_000, dueDate = LocalDate.of(2026, 10, 24), recorded = false)

    /** 월초에 이미 나간 구독료 */
    private val smallSubscription = FixedCharge(amount = 20_000, dueDate = LocalDate.of(2026, 9, 27), recorded = true)

    private fun calc(
        budget: Long = 500_000,
        includeFixed: Boolean = true,
        today: LocalDate = day10,
        variableSpent: Long,
        fixedSpent: Long = 20_000,
        fixed: List<FixedCharge> = listOf(bigSubscription, smallSubscription),
    ) = BudgetCalculator.calculate(budget, includeFixed, october, today, variableSpent, fixedSpent, fixed)

    @Test
    fun `날짜 계산 - 30일 중 10일째, 오늘 포함 21일 남음`() {
        val s = calc(variableSpent = 0)
        assertEquals(30, s.totalDays)
        assertEquals(10, s.elapsedDays)
        assertEquals(21, s.daysLeft)
    }

    @Test
    fun `고정지출 포함 - 나갈 고정지출을 미리 빼고 남은 예산과 하루 권장액을 계산한다`() {
        val s = calc(variableSpent = 60_000)

        assertEquals(80_000, s.spent) // 변동 60,000 + 이미 나간 구독료 20,000
        assertEquals(100_000, s.upcomingFixed) // 월말 구독료
        assertEquals(320_000, s.remaining) // 500,000 - 80,000 - 100,000
        assertEquals(15_200, s.dailyAllowance) // 320,000 / 21 = 15,238 → 백 원 아래 버림
        assertEquals(BudgetStatus.ON_TRACK, s.status)
    }

    @Test
    fun `고정지출 포함 - 적정선은 결제일이 온 고정지출 + 나머지 예산을 날짜로 나눈 몫`() {
        val s = calc(variableSpent = 60_000)
        // 20,000 + (500,000 - 120,000) * 10 / 30 = 20,000 + 126,666
        assertEquals(146_666, s.expectedByToday)
    }

    @Test
    fun `적정선보다 많이 쓰면 주의`() {
        val s = calc(variableSpent = 140_000) // 160,000 > 146,666
        assertEquals(BudgetStatus.AHEAD_OF_PACE, s.status)
    }

    @Test
    fun `예산은 안 넘었어도 나갈 고정지출까지 합쳐 모자라면 초과`() {
        val s = calc(variableSpent = 400_000) // 420,000 쓰고 100,000 더 나갈 예정
        assertEquals(-20_000, s.remaining)
        assertEquals(0, s.dailyAllowance)
        assertEquals(BudgetStatus.OVER, s.status)
    }

    @Test
    fun `마지막 날 큰 고정지출이 나가도 적정선이 같이 올라가서 초과로 보이지 않는다`() {
        val lastDay = LocalDate.of(2026, 10, 24)
        val s = calc(
            today = lastDay,
            variableSpent = 370_000,
            fixedSpent = 120_000,
            fixed = listOf(bigSubscription.copy(recorded = true), smallSubscription),
        )
        assertEquals(1, s.daysLeft)
        assertEquals(500_000, s.expectedByToday)
        assertEquals(490_000, s.spent)
        assertEquals(BudgetStatus.ON_TRACK, s.status)
    }

    @Test
    fun `고정지출 제외 - 고정지출은 따로 보고 변동 지출만 예산과 비교한다`() {
        val s = calc(budget = 300_000, includeFixed = false, variableSpent = 80_000)

        assertEquals(80_000, s.spent)
        assertEquals(20_000, s.separateFixedSpent)
        assertEquals(0, s.upcomingFixed)
        assertEquals(220_000, s.remaining)
        assertEquals(100_000, s.expectedByToday) // 300,000 * 10 / 30
        assertEquals(BudgetStatus.ON_TRACK, s.status)
    }

    @Test
    fun `하루 권장액은 백 원 단위로 버린다`() {
        // 남은 예산 300,000원 (500,000 - 200,000), 오늘 포함 29일 → 10,344원 → 10,300원
        val s = calc(
            today = LocalDate.of(2026, 9, 26),
            variableSpent = 0,
            fixedSpent = 0,
            fixed = listOf(FixedCharge(200_000, LocalDate.of(2026, 10, 24), recorded = false)),
        )
        assertEquals(300_000, s.remaining)
        assertEquals(29, s.daysLeft)
        assertEquals(10_300, s.dailyAllowance)
    }

    @Test
    fun `예산을 넘으면 초과`() {
        val s = calc(budget = 300_000, includeFixed = false, variableSpent = 310_000)
        assertEquals(BudgetStatus.OVER, s.status)
        assertEquals(103, s.usedPercent)
    }
}
