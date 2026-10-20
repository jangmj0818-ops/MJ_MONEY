package com.mj.money.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyCountTest {

    private fun existing(id: Long, name: String, recorded: Long, actual: Long = recorded, newName: String = name, removed: Boolean = false) =
        PocketCountInput(id, name, newName, recorded, actual, removed)

    private fun new(name: String, actual: Long, removed: Boolean = false) =
        PocketCountInput(null, "", name, 0, actual, removed)

    @Test
    fun `처음 등록 - 장소별 금액이 새 장소로 들어간다`() {
        val plan = planCount(listOf(new("서랍", 300_000), new(" 봉투 ", 250_000), new("차", 0)))
        assertEquals(listOf("서랍" to 300_000L, "봉투" to 250_000L, "차" to 0L), plan.newPockets)
        assertTrue(plan.adjustments.isEmpty())
    }

    @Test
    fun `이름 없는 새 줄과 지운 새 줄은 무시한다`() {
        val plan = planCount(listOf(new("", 0), new("  ", 10_000), new("금고", 50_000, removed = true)))
        assertTrue(plan.isEmpty)
    }

    @Test
    fun `세어 본 금액이 다르면 차이만큼만 맞춘다`() {
        val plan = planCount(
            listOf(
                existing(1, "지갑", recorded = 48_000, actual = 50_000),
                existing(2, "서랍", recorded = 300_000, actual = 270_000),
                existing(3, "차", recorded = 100_000),
            )
        )
        assertEquals(listOf(1L to 2_000L, 2L to -30_000L), plan.adjustments)
        assertTrue(plan.newPockets.isEmpty())
    }

    @Test
    fun `지운 장소는 0으로 맞추고 숨긴다`() {
        val plan = planCount(listOf(existing(3, "차", recorded = 100_000, removed = true)))
        assertEquals(listOf(3L to -100_000L), plan.adjustments)
        assertEquals(listOf(3L), plan.hides)
    }

    @Test
    fun `이름을 바꾸면 이름만 바꾼다`() {
        val plan = planCount(listOf(existing(2, "서랍", recorded = 300_000, newName = "안방 서랍")))
        assertEquals(listOf(2L to "안방 서랍"), plan.renames)
        assertTrue(plan.adjustments.isEmpty())
    }

    @Test
    fun `바뀐 게 없으면 할 일도 없다`() {
        assertTrue(planCount(listOf(existing(1, "지갑", 50_000))).isEmpty)
    }
}
