package com.mj.money.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CategoryEntity::class,
        PaymentMethodEntity::class,
        TransactionEntity::class,
        NotificationLogEntity::class,
        RecurringExpenseEntity::class,
        EmergencyPocketEntity::class,
        EmergencyMoveEntity::class,
        MerchantRuleEntity::class,
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        // 2: 고정지출 테이블 추가
        AutoMigration(from = 1, to = 2),
        // 3: 비상금 테이블 추가 + '비상금' 결제수단
        AutoMigration(from = 2, to = 3, spec = MjMoneyDatabase.AddEmergencyFund::class),
        // 4: 카테고리 차트 색 번호 (열 추가만 하므로 기존 데이터는 그대로)
        AutoMigration(from = 3, to = 4, spec = MjMoneyDatabase.AddCategoryColorSlots::class),
        // 5: 가맹점 → 카테고리 학습 테이블
        AutoMigration(from = 4, to = 5),
    ],
)
abstract class MjMoneyDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun paymentMethodDao(): PaymentMethodDao
    abstract fun notificationLogDao(): NotificationLogDao
    abstract fun recurringExpenseDao(): RecurringExpenseDao
    abstract fun emergencyDao(): EmergencyDao
    abstract fun merchantRuleDao(): MerchantRuleDao

    class AddEmergencyFund : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            DefaultData.insertEmergencyPaymentMethod(db)
        }
    }

    class AddCategoryColorSlots : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            DefaultData.assignColorSlots(db)
        }
    }

    companion object {
        fun create(context: Context): MjMoneyDatabase =
            Room.databaseBuilder(context, MjMoneyDatabase::class.java, "mjmoney.db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        DefaultData.seed(db)
                    }
                })
                .build()
    }
}
