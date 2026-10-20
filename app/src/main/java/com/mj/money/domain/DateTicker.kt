package com.mj.money.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** 오늘 날짜를 내보내고, 자정이 지나면 새 날짜를 다시 내보낸다. (화면을 켜둔 채 날이 바뀌어도 맞게) */
fun dateTicker(): Flow<LocalDate> = flow {
    while (true) {
        val now = LocalDateTime.now()
        emit(now.toLocalDate())
        val nextDay = now.toLocalDate().plusDays(1).atStartOfDay()
        delay(Duration.between(now, nextDay).toMillis() + 1_000)
    }
}
