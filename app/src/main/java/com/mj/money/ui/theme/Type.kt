package com.mj.money.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography()

/** 목록처럼 세로로 줄 맞춰야 하는 금액은 고정폭 숫자(tnum)를 쓴다. */
val AmountStyle = TextStyle(fontFeatureSettings = "tnum")

/** 화면을 대표하는 큰 숫자. 크게 쓸 때는 고정폭 숫자가 헐거워 보여서 기본 숫자를 쓴다. */
val HeroAmountStyle = TextStyle(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold)
