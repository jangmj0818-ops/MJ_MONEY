package com.mj.money.data.db

import androidx.sqlite.db.SupportSQLiteDatabase

/** 앱을 처음 설치했을 때 넣는 기본 카테고리와 결제수단 (설정에서 편집 가능하게 할 예정) */
object DefaultData {

    /**
     * colorSlot: 차트 색 번호 (1~8 = 검증된 8색 팔레트, 0 = 회색).
     * 카테고리가 8개보다 많아서 자주 같이 크지 않을 카테고리끼리 색을 나눠 쓴다.
     * 색만으로 구분하지 않도록 차트에는 이모지와 범례를 함께 쓴다.
     */
    private data class Category(val name: String, val emoji: String, val color: Long, val colorSlot: Int)

    private val expenseCategories = listOf(
        Category("식비", "🍚", 0xFF378ADD, 1),
        Category("카페·간식", "☕", 0xFFBA7517, 4),
        Category("교통", "🚌", 0xFFD85A30, 3),
        Category("쇼핑", "🛍️", 0xFF1D9E75, 5),
        Category("의료·건강", "💊", 0xFFE24B4A, 3),
        Category("문화·여가", "🎬", 0xFF7F77DD, 8),
        Category("취미", "🎨", 0xFF639922, 6),
        Category("OTT·구독", "▶️", 0xFF993C1D, 2),
        Category("AI", "✨", 0xFFD4537E, 7),
        Category("교육·육아", "📚", 0xFF185FA5, 6),
        Category("경조사·선물", "🎁", 0xFF993556, 5),
        Category("기타", "📦", 0xFF888780, 0),
    )

    private val incomeCategories = listOf(
        Category("급여", "💰", 0xFF1D9E75, 3),
        Category("부수입", "💵", 0xFF378ADD, 1),
        Category("용돈", "🪙", 0xFFBA7517, 4),
        Category("기타", "📥", 0xFF888780, 0),
    )

    private val paymentMethods = listOf(
        "삼성카드" to PaymentKind.CREDIT_CARD,
        "카카오뱅크 체크" to PaymentKind.CHECK_CARD,
        "카카오뱅크 계좌" to PaymentKind.ACCOUNT,
        "카카오페이" to PaymentKind.PAY,
        "네이버페이" to PaymentKind.PAY,
        "현금" to PaymentKind.CASH,
    )

    fun seed(db: SupportSQLiteDatabase) {
        var order = 0
        expenseCategories.forEach { insertCategory(db, it, CategoryType.EXPENSE, order++) }
        incomeCategories.forEach { insertCategory(db, it, CategoryType.INCOME, order++) }
        paymentMethods.forEachIndexed { index, (name, kind) ->
            db.execSQL(
                "INSERT INTO payment_methods (name, kind, sortOrder, hidden) VALUES (?, ?, ?, 0)",
                arrayOf<Any>(name, kind.name, index),
            )
        }
        insertEmergencyPaymentMethod(db)
    }

    /** '비상금' 결제수단을 맨 뒤에 추가한다. (새로 설치할 때와 DB 2 → 3 업그레이드 때) */
    fun insertEmergencyPaymentMethod(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO payment_methods (name, kind, sortOrder, hidden)
            VALUES (?, ?, (SELECT IFNULL(MAX(sortOrder), -1) + 1 FROM payment_methods), 0)
            """.trimIndent(),
            arrayOf<Any>("비상금", PaymentKind.EMERGENCY.name),
        )
    }

    /** DB 3 → 4 업그레이드 때 기본 카테고리에 색 번호를 채운다. */
    fun assignColorSlots(db: SupportSQLiteDatabase) {
        val all = expenseCategories.map { it to CategoryType.EXPENSE } + incomeCategories.map { it to CategoryType.INCOME }
        all.forEach { (category, type) ->
            db.execSQL(
                "UPDATE categories SET colorSlot = ? WHERE name = ? AND type = ?",
                arrayOf<Any>(category.colorSlot, category.name, type.name),
            )
        }
    }

    private fun insertCategory(db: SupportSQLiteDatabase, c: Category, type: CategoryType, order: Int) {
        db.execSQL(
            "INSERT INTO categories (name, type, emoji, color, colorSlot, sortOrder, hidden) VALUES (?, ?, ?, ?, ?, ?, 0)",
            arrayOf<Any>(c.name, type.name, c.emoji, c.color, c.colorSlot, order),
        )
    }
}
