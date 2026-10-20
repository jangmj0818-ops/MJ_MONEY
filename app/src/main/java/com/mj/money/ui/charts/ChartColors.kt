package com.mj.money.ui.charts

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 차트 색. dataviz 가이드의 검증된 기본 팔레트를 그대로 쓴다. (라이트·다크 각각 검증된 값)
 * - 범주(카테고리) 8색: 순서가 색각 이상 구분을 보장하는 장치라서 순서를 바꾸지 않는다.
 * - 순차(히트맵): 파랑 한 가지, 적을수록 배경 쪽으로 옅어진다.
 */
object ChartColors {
    private val categoricalLight = listOf(
        Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A), Color(0xFFEDA100),
        Color(0xFFE87BA4), Color(0xFF008300), Color(0xFF4A3AA7), Color(0xFFE34948),
    )
    private val categoricalDark = listOf(
        Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500),
        Color(0xFFD55181), Color(0xFF008300), Color(0xFF9085E9), Color(0xFFE66767),
    )

    // 1~4단계. 라이트는 연→진, 다크는 배경에 가까운 어두운 파랑→밝은 파랑.
    private val heatLight = listOf(Color(0xFFCDE2FB), Color(0xFF86B6EF), Color(0xFF3987E5), Color(0xFF1C5CAB))
    private val heatDark = listOf(Color(0xFF184F95), Color(0xFF256ABF), Color(0xFF3987E5), Color(0xFF86B6EF))

    private val otherLight = Color(0xFFB4B2A9)
    private val otherDark = Color(0xFF6B6A64)
    private val mutedLight = Color(0xFFC3C2B7)
    private val mutedDark = Color(0xFF4A4A46)

    @Composable
    @ReadOnlyComposable
    private fun isDark(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    /** 카테고리 색 번호(1~8)의 색. 0이나 범위 밖이면 '그 외' 회색. */
    @Composable
    @ReadOnlyComposable
    fun category(slot: Int): Color {
        val list = if (isDark()) categoricalDark else categoricalLight
        return list.getOrNull(slot - 1) ?: other()
    }

    /** '그 외'·기타 */
    @Composable
    @ReadOnlyComposable
    fun other(): Color = if (isDark()) otherDark else otherLight

    /** 강조하지 않는 막대 (지난달들) */
    @Composable
    @ReadOnlyComposable
    fun deemphasis(): Color = if (isDark()) mutedDark else mutedLight

    /** 히트맵 1~4단계 색 */
    @Composable
    @ReadOnlyComposable
    fun heat(level: Int): Color {
        val list = if (isDark()) heatDark else heatLight
        return list[(level - 1).coerceIn(0, list.lastIndex)]
    }

    /** 칠해진 칸 위의 글자색: 밝은 칸엔 검정, 어두운 칸엔 흰색 */
    fun textOn(fill: Color): Color = if (fill.luminance() > 0.4f) Color(0xFF0B0B0B) else Color.White
}
