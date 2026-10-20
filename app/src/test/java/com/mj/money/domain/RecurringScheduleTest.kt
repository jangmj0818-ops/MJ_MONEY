package com.mj.money.domain

import com.mj.money.data.db.RecurringExpenseEntity
import com.mj.money.data.db.RecurringStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class RecurringScheduleTest {

    private val payday = PeriodCalculator(startDay = 25)
    private val october = payday.periodFor(YearMonth.of(2026, 10)) // 9.25 ~ 10.24

    private fun date(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d)

    private fun expense(
        dayOfMonth: Int,
        start: LocalDate,
        lastRecorded: LocalDate? = null,
        trialEnd: LocalDate? = null,
        autoRecord: Boolean = true,
        status: RecurringStatus = RecurringStatus.ACTIVE,
    ) = RecurringExpenseEntity(
        name = "넷플릭스",
        amount = 17_000,
        categoryId = null,
        paymentMethodId = null,
        dayOfMonth = dayOfMonth,
        autoRecord = autoRecord,
        trialEndEpochDay = trialEnd?.toEpochDay(),
        status = status,
        startEpochDay = start.toEpochDay(),
        lastRecordedEpochDay = lastRecorded?.toEpochDay(),
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun `결제일이 없는 달은 말일로 본다`() {
        assertEquals(date(2027, 2, 28), RecurringSchedule.dueDateIn(YearMonth.of(2027, 2), 31))
        assertEquals(date(2028, 2, 29), RecurringSchedule.dueDateIn(YearMonth.of(2028, 2), 30))
        assertEquals(date(2026, 4, 30), RecurringSchedule.dueDateIn(YearMonth.of(2026, 4), 31))
    }

    @Test
    fun `가계부 한 달 안의 결제일은 시작일 전후로 나뉜다`() {
        assertEquals(date(2026, 9, 27), RecurringSchedule.dueDateIn(october, 27))
        assertEquals(date(2026, 9, 25), RecurringSchedule.dueDateIn(october, 25))
        assertEquals(date(2026, 10, 1), RecurringSchedule.dueDateIn(october, 1))
        assertEquals(date(2026, 10, 24), RecurringSchedule.dueDateIn(october, 24))
        assertEquals(date(2026, 9, 30), RecurringSchedule.dueDateIn(october, 31))
    }

    @Test
    fun `2월이 끼어도 한 달에 결제일은 하나다`() {
        val march = payday.periodFor(YearMonth.of(2027, 3)) // 2.25 ~ 3.24
        assertEquals(date(2027, 2, 28), RecurringSchedule.dueDateIn(march, 31))
        assertEquals(date(2027, 3, 3), RecurringSchedule.dueDateIn(march, 3))
    }

    @Test
    fun `기간 사이의 결제일들 (시작일은 빼고 끝날은 포함)`() {
        assertEquals(
            listOf(date(2026, 9, 27), date(2026, 10, 27), date(2026, 11, 27)),
            RecurringSchedule.dueDatesBetween(27, date(2026, 9, 25), date(2026, 11, 27)),
        )
        assertEquals(
            listOf(date(2026, 10, 25)),
            RecurringSchedule.dueDatesBetween(25, date(2026, 9, 25), date(2026, 10, 25)),
        )
        assertTrue(RecurringSchedule.dueDatesBetween(27, date(2026, 9, 27), date(2026, 10, 26)).isEmpty())
    }

    @Test
    fun `다음 결제일`() {
        assertEquals(date(2026, 9, 27), RecurringSchedule.nextDueDate(27, date(2026, 9, 25)))
        assertEquals(date(2026, 10, 27), RecurringSchedule.nextDueDate(27, date(2026, 9, 27)))
        assertEquals(date(2027, 2, 28), RecurringSchedule.nextDueDate(31, date(2027, 2, 15)))
        assertEquals(date(2027, 3, 31), RecurringSchedule.nextDueDate(31, date(2027, 2, 28)))
    }

    @Test
    fun `등록한 날의 결제일은 자동 기록하지 않고 다음 결제일부터 기록한다`() {
        val melon = expense(dayOfMonth = 25, start = date(2026, 9, 25))
        assertTrue(RecurringSchedule.pendingAutoRecordDates(melon, date(2026, 9, 25)).isEmpty())
        assertEquals(
            listOf(date(2026, 10, 25)),
            RecurringSchedule.pendingAutoRecordDates(melon, date(2026, 10, 25)),
        )
    }

    @Test
    fun `앱을 오래 안 열었으면 밀린 결제일을 모두 기록한다`() {
        val netflix = expense(dayOfMonth = 27, start = date(2026, 9, 25))
        assertEquals(
            listOf(date(2026, 9, 27), date(2026, 10, 27), date(2026, 11, 27)),
            RecurringSchedule.pendingAutoRecordDates(netflix, date(2026, 11, 30)),
        )
    }

    @Test
    fun `이미 기록한 결제일은 다시 기록하지 않는다`() {
        val netflix = expense(dayOfMonth = 27, start = date(2026, 9, 25), lastRecorded = date(2026, 10, 27))
        assertEquals(
            listOf(date(2026, 11, 27)),
            RecurringSchedule.pendingAutoRecordDates(netflix, date(2026, 11, 30)),
        )
    }

    @Test
    fun `무료 체험 기간의 결제일은 기록하지 않는다`() {
        val trial = expense(dayOfMonth = 27, start = date(2026, 9, 25), trialEnd = date(2026, 10, 27))
        assertEquals(
            listOf(date(2026, 11, 27)),
            RecurringSchedule.pendingAutoRecordDates(trial, date(2026, 11, 30)),
        )
        assertTrue(RecurringSchedule.isInTrial(trial, date(2026, 10, 27)))
        assertFalse(RecurringSchedule.isInTrial(trial, date(2026, 10, 28)))
    }

    @Test
    fun `자동 기록을 껐거나 해지한 항목은 기록하지 않는다`() {
        val today = date(2026, 11, 30)
        assertTrue(RecurringSchedule.pendingAutoRecordDates(expense(27, date(2026, 9, 25), autoRecord = false), today).isEmpty())
        assertTrue(
            RecurringSchedule.pendingAutoRecordDates(
                expense(27, date(2026, 9, 25), status = RecurringStatus.CANCELED),
                today,
            ).isEmpty()
        )
    }
}
