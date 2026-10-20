package com.mj.money.data

import androidx.room.withTransaction
import com.mj.money.data.db.CategoryEntity
import com.mj.money.data.db.MjMoneyDatabase
import com.mj.money.data.db.PaymentMethodEntity

/** 카테고리·결제수단 편집 (추가, 수정, 순서 바꾸기, 숨기기) */
class CatalogRepository(private val db: MjMoneyDatabase) {
    private val categoryDao = db.categoryDao()
    private val paymentMethodDao = db.paymentMethodDao()

    suspend fun saveCategory(category: CategoryEntity): Long =
        if (category.id == 0L) {
            categoryDao.insert(category.copy(sortOrder = categoryDao.nextSortOrder()))
        } else {
            categoryDao.update(category)
            category.id
        }

    suspend fun savePaymentMethod(method: PaymentMethodEntity): Long =
        if (method.id == 0L) {
            paymentMethodDao.insert(method.copy(sortOrder = paymentMethodDao.nextSortOrder()))
        } else {
            paymentMethodDao.update(method)
            method.id
        }

    /** 같은 목록 안에서 바로 옆 항목과 순서를 맞바꾼다. */
    suspend fun swapCategories(a: CategoryEntity, b: CategoryEntity) = db.withTransaction {
        categoryDao.update(a.copy(sortOrder = b.sortOrder))
        categoryDao.update(b.copy(sortOrder = a.sortOrder))
    }

    suspend fun swapPaymentMethods(a: PaymentMethodEntity, b: PaymentMethodEntity) = db.withTransaction {
        paymentMethodDao.update(a.copy(sortOrder = b.sortOrder))
        paymentMethodDao.update(b.copy(sortOrder = a.sortOrder))
    }
}
