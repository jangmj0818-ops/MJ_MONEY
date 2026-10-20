package com.mj.money.notification

import com.mj.money.notification.NotificationCapturePolicy.RULE_AMOUNT
import com.mj.money.notification.NotificationCapturePolicy.RULE_KAKAOTALK
import com.mj.money.notification.NotificationCapturePolicy.RULE_KNOWN_APP
import com.mj.money.notification.NotificationCapturePolicy.RULE_NAVER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCapturePolicyTest {

    private fun match(pkg: String, title: String?, text: String?, subText: String? = null) =
        NotificationCapturePolicy.match(pkg, title, text, subText)

    @Test
    fun `후보 카드·페이 앱의 알림은 모두 모은다`() {
        assertEquals(RULE_KNOWN_APP, match("com.kakaobank.channel", "카카오뱅크", "체크카드 승인 12,000원"))
        assertEquals(RULE_KNOWN_APP, match("com.kakaopay.app", "이벤트", "포인트를 받아가세요"))
    }

    @Test
    fun `카카오톡은 카드사·페이 채널이 보낸 금액 알림만 모은다`() {
        assertEquals(RULE_KAKAOTALK, match("com.kakao.talk", "삼성카드", "삼성카드 승인 45,000원 일시불 스타벅스"))
        assertNull(match("com.kakao.talk", "친구", "삼성카드로 45,000원 결제했어"))
        assertNull(match("com.kakao.talk", "삼성카드", "이번 달 혜택을 확인하세요"))
    }

    @Test
    fun `모르는 앱이라도 금액과 결제 단어가 함께 있으면 모은다`() {
        assertEquals(RULE_AMOUNT, match("com.samsung.android.messaging", "15881688", "[삼성카드] 승인 12,300원 일시불"))
        assertEquals(RULE_AMOUNT, match("com.example.bank", "입금", "3,500,000원이 입금되었습니다"))
    }

    @Test
    fun `금액이나 결제 단어가 없으면 모으지 않는다`() {
        assertNull(match("com.example.news", "속보", "오늘의 뉴스"))
        assertNull(match("com.example.shop", "할인", "최대 50,000원 쿠폰"))
    }

    @Test
    fun `네이버 앱은 결제 관련 알림만 모은다`() {
        assertEquals(RULE_NAVER, match("com.nhn.android.search", "네이버페이", "결제가 완료되었어요"))
        assertEquals(RULE_NAVER, match("com.nhn.android.search", "Npay", "주문하신 상품 12,000원"))
        assertNull(match("com.nhn.android.search", "오늘의 뉴스", "주요 소식을 확인하세요"))
    }

    @Test
    fun `진단용 결제 관련 앱 판별`() {
        assertTrue(NotificationCapturePolicy.isFinanceRelated("com.kakaobank.channel"))
        assertTrue(NotificationCapturePolicy.isFinanceRelated("com.nhn.android.search"))
        assertTrue(NotificationCapturePolicy.isFinanceRelated("com.samsung.android.messaging"))
        assertFalse(NotificationCapturePolicy.isFinanceRelated("com.example.news"))
    }

    @Test
    fun `다른 메신저의 대화는 모으지 않는다`() {
        assertNull(match("org.telegram.messenger", "친구", "택시비 12,000원 입금 부탁해"))
    }
}
