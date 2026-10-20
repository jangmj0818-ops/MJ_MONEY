package com.mj.money.notification.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 실제 알림 형식을 본뜬 예시 (이름·계좌·금액은 가짜) */
class PaymentParserTest {

    private val kakaobank = "com.kakaobank.channel"
    private val kakaotalk = "com.kakao.talk"
    private val naver = "com.nhn.android.search"

    private fun parse(pkg: String, title: String?, text: String?) = PaymentParser.parse(pkg, title, text, null)

    private fun payment(outcome: ParseOutcome): ParseOutcome.Payment {
        assertTrue("Payment가 아님: $outcome", outcome is ParseOutcome.Payment)
        return outcome as ParseOutcome.Payment
    }

    private fun assertIgnored(outcome: ParseOutcome) = assertTrue("Ignore가 아님: $outcome", outcome is ParseOutcome.Ignore)

    @Test
    fun `카카오뱅크 체크카드 결제`() {
        val p = payment(parse(kakaobank, "카드 결제 12,300원", "체크카드(1111) | 테스트분식"))
        assertEquals(Direction.EXPENSE, p.direction)
        assertEquals(12_300, p.amount)
        assertEquals("테스트분식", p.merchant)
        assertEquals(MethodHint(MethodKind.KAKAOBANK_CARD, "1111"), p.method)
    }

    @Test
    fun `카카오뱅크 체크카드 결제 취소`() {
        assertEquals(Direction.CANCEL, payment(parse(kakaobank, "카드 결제 취소 12,300원", "체크카드(1111) | 테스트분식")).direction)
    }

    @Test
    fun `카카오페이 출금은 카카오페이 지출`() {
        val p = payment(parse(kakaobank, "출금 15,000원", "입출금통장(2222) → 카카오페이"))
        assertEquals(MethodKind.KAKAOPAY, p.method.kind)
        assertEquals(null, p.merchant)
    }

    @Test
    fun `토스페이·가맹점·사람에게 보낸 돈은 계좌 지출`() {
        val toss = payment(parse(kakaobank, "출금 1,700원", "입출금통장(2222) → 토스페이"))
        assertEquals(MethodHint(MethodKind.KAKAOBANK_ACCOUNT, "2222"), toss.method)
        assertEquals("토스페이", toss.merchant)
        assertEquals("홍길동", payment(parse(kakaobank, "출금 20,000원", "입출금통장(2222) → 홍길동")).merchant)
    }

    @Test
    fun `내 계좌 간 이동, 카드값, 페이 충전, 모임통장은 기록하지 않는다`() {
        assertIgnored(parse(kakaobank, "출금 400,000원", "입출금통장(2222) → 세이프박스"))
        assertIgnored(parse(kakaobank, "입금 100,000원", "세이프박스 → 입출금통장(2222)"))
        assertIgnored(parse(kakaobank, "출금 47,000원", "입출금통장(2222) → 삼성카드"))
        assertIgnored(parse(kakaobank, "출금 300,000원", "입출금통장(2222) → 하나카드결제"))
        assertIgnored(parse(kakaobank, "출금 10,000원", "입출금통장(2222) → 네이버페이충전"))
        assertIgnored(parse(kakaobank, "모임통장 출금 1,000,000원", "모임통장(3333) → 세이프박스"))
    }

    @Test
    fun `다른 사람이 보낸 돈은 수입`() {
        val p = payment(parse(kakaobank, "입금 50,000원", "홍길동 → 입출금통장(2222)"))
        assertEquals(Direction.INCOME, p.direction)
        assertEquals("홍길동", p.merchant)
    }

    @Test
    fun `카카오뱅크 안내·혜택·요약 알림은 무시`() {
        assertIgnored(parse(kakaobank, "최대 5만원 결제 혜택 도착!", "결제 혜택은 이틀 동안 확인 가능해요."))
        assertIgnored(parse(kakaobank, null, null))
        assertIgnored(parse(kakaobank, "삼성카드 자동이체 부분출금 안내", "입출금통장(2222) 잔액부족으로 삼성카드 자동이체가 일부만 출금됐어요."))
    }

    @Test
    fun `카카오톡 알림톡의 카카오뱅크 출금`() {
        val p = payment(parse(kakaotalk, "카카오뱅크", "10/01 12:19\n출금 21,500원\n입출금통장(2222) → 테스트식당"))
        assertEquals(21_500, p.amount)
        assertEquals("테스트식당", p.merchant)
        assertIgnored(parse(kakaotalk, "카카오뱅크", "10/02 17:48\n입금 100,000원\n세이프박스 → 입출금통장(2222)"))
    }

    @Test
    fun `카카오톡 삼성카드 미납 안내는 결제가 아니다`() {
        assertIgnored(parse(kakaotalk, "삼성카드", "[삼성카드] 결제대금 미입금 안내\n미납금액 : 100,000원"))
    }

    @Test
    fun `네이버페이 결제`() {
        val p = payment(parse(naver, "네이버페이", "테스트베이커리에서 14,790원 결제되었습니다. (결제 수단: 카드 간편결제)"))
        assertEquals(14_790, p.amount)
        assertEquals("테스트베이커리", p.merchant)
        assertEquals(MethodKind.NAVERPAY, p.via?.kind)
    }

    @Test
    fun `네이버 주문 알림은 금액 없이 가맹점만`() {
        val outcome = parse(naver, "(주)테스트배달", "치킨 외 2개  상품이 결제되었습니다.")
        assertEquals(ParseOutcome.MerchantOnly("(주)테스트배달", "치킨 외 2개"), outcome)
    }

    @Test
    fun `네이버 적립·멤버십 안내는 무시`() {
        assertIgnored(parse(naver, "네이버페이", "포인트 적립을 받을 수 있는 결제가 5건 있어요."))
        assertIgnored(parse(naver, "네이버플러스 멤버십", "테스트 스트리밍 혜택이 결제되었습니다."))
        assertIgnored(parse(naver, "네이버페이", "[네이버플러스 멤버십] 테스트 스트리밍 10월 17일 포인트 79원 적립 예정입니다."))
    }

    @Test
    fun `네이버 멤버십 결제 문자`() {
        val p = payment(PaymentParser.parse("com.samsung.android.messaging", "네이버플러스 멤버십", "[Web발신]\n[네이버 멤버십] 테스트 스트리밍 7,900원 결제", null))
        assertEquals(7_900, p.amount)
        assertEquals("테스트 스트리밍", p.merchant)
    }

    @Test
    fun `티머니는 교통, 0원은 무시`() {
        val p = payment(PaymentParser.parse("com.lgt.tmoney", "결제완료", "1,450원 결제 되었습니다.", null))
        assertEquals("교통", p.categoryHint)
        assertIgnored(PaymentParser.parse("com.lgt.tmoney", "결제완료", "0원 결제 되었습니다.", null))
    }

    @Test
    fun `삼성카드 승인 알림`() {
        val p = payment(PaymentParser.parse("net.ib.android.smcard", "삼성카드 승인", "삼성1111승인 홍*동 12,000원 일시불 10/04 12:30 테스트마트 누적 300,000원", null))
        assertEquals(12_000, p.amount)
        assertEquals("테스트마트", p.merchant)
        assertEquals(MethodKind.SAMSUNG_CARD, p.method.kind)
    }

    @Test
    fun `은행 계좌 이동은 기록하지 않는다`() {
        assertIgnored(PaymentParser.parse("com.kbstar.kbbank", "출금 1,000,000원", "홍*동님 10/01 18:11 카드출금 1,000,000", null))
    }
}
