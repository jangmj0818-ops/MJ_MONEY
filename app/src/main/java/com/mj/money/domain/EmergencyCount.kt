package com.mj.money.domain

/** 세어 보기 화면의 한 줄 */
data class PocketCountInput(
    /** 새로 추가한 장소면 null */
    val pocketId: Long?,
    val originalName: String,
    val name: String,
    /** 기록상 금액 */
    val recorded: Long,
    /** 실제로 세어 본 금액 */
    val actual: Long,
    val removed: Boolean = false,
)

data class CountPlan(
    /** 새 장소 이름과 처음 금액 */
    val newPockets: List<Pair<String, Long>> = emptyList(),
    val renames: List<Pair<Long, String>> = emptyList(),
    /** 장소 id와 맞춰야 할 금액 차이 */
    val adjustments: List<Pair<Long, Long>> = emptyList(),
    val hides: List<Long> = emptyList(),
) {
    val isEmpty: Boolean get() = newPockets.isEmpty() && renames.isEmpty() && adjustments.isEmpty() && hides.isEmpty()
}

/**
 * 세어 본 금액과 기록을 비교해서 할 일을 정한다.
 * - 금액이 다르면 차이만큼 보정한다.
 * - 지운 장소는 금액을 0으로 맞추고 숨긴다. (지난 기록은 남긴다)
 * - 이름 없는 새 줄은 무시한다.
 */
fun planCount(inputs: List<PocketCountInput>): CountPlan {
    val newPockets = mutableListOf<Pair<String, Long>>()
    val renames = mutableListOf<Pair<Long, String>>()
    val adjustments = mutableListOf<Pair<Long, Long>>()
    val hides = mutableListOf<Long>()

    for (input in inputs) {
        val name = input.name.trim()
        val id = input.pocketId
        when {
            id == null -> if (!input.removed && name.isNotEmpty()) newPockets += name to input.actual
            input.removed -> {
                if (input.recorded != 0L) adjustments += id to -input.recorded
                hides += id
            }
            else -> {
                if (name.isNotEmpty() && name != input.originalName) renames += id to name
                val delta = input.actual - input.recorded
                if (delta != 0L) adjustments += id to delta
            }
        }
    }
    return CountPlan(newPockets, renames, adjustments, hides)
}
