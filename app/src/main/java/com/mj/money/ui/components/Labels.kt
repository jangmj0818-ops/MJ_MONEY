package com.mj.money.ui.components

import com.mj.money.data.db.PaymentKind

fun PaymentKind.label(): String = when (this) {
    PaymentKind.CREDIT_CARD -> "신용카드"
    PaymentKind.CHECK_CARD -> "체크카드"
    PaymentKind.ACCOUNT -> "계좌"
    PaymentKind.CASH -> "현금"
    PaymentKind.PAY -> "페이"
    PaymentKind.EMERGENCY -> "비상금"
}

/** 카드 실적 목표를 둘 수 있는 종류 */
val PaymentKind.isCard: Boolean get() = this == PaymentKind.CREDIT_CARD || this == PaymentKind.CHECK_CARD
