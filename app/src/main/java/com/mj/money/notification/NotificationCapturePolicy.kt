package com.mj.money.notification

/**
 * 어떤 알림을 원문 로그로 모을지 정한다.
 *
 * 카드사·페이 앱의 실제 알림 문구를 아직 모르기 때문에 넓게 모은다.
 * 6단계에서 모인 로그를 보고 파싱 규칙을 확정한다.
 */
object NotificationCapturePolicy {

    const val RULE_KNOWN_APP = "known_app"
    const val RULE_KAKAOTALK = "kakaotalk"
    const val RULE_NAVER = "naver"
    const val RULE_AMOUNT = "amount"

    /** 결제 앱 → 표시 이름. 이 앱들의 알림은 내용과 상관없이 모두 모은다. */
    val KNOWN_APPS: Map<String, String> = mapOf(
        "net.ib.android.smcard" to "삼성카드",
        "kr.co.samsungcard.mpocket" to "삼성카드",
        "com.kakaobank.channel" to "카카오뱅크",
        "com.kakaopay.app" to "카카오페이",
        "com.naverfin.payapp" to "네이버페이",
    )

    private const val KAKAOTALK = "com.kakao.talk"

    /** 네이버페이 결제 알림이 네이버페이 앱이 아니라 네이버 앱으로 오기도 한다. */
    private const val NAVER_APP = "com.nhn.android.search"

    /** 카카오톡 알림톡으로 결제 안내가 오는 경우, 알림 제목(보낸 채널 이름)에 들어가는 키워드 */
    private val KAKAOTALK_SENDERS = listOf("삼성카드", "카카오뱅크", "카카오페이", "네이버페이")

    /** 네이버 앱 알림 중 결제와 관련된 것만 (뉴스·카페 알림은 모으지 않는다) */
    private val NAVER_PAYMENT_WORDS = Regex("네이버\\s?페이|N\\s?페이|npay|결제|주문|송금|환불|승인|출금|입금", RegexOption.IGNORE_CASE)

    /** 개인 대화가 섞이지 않도록 금액 규칙에서 빼는 메신저 앱 */
    private val MESSENGERS = setOf(
        KAKAOTALK,
        "com.facebook.orca",
        "com.instagram.android",
        "jp.naver.line.android",
        "org.telegram.messenger",
        "com.whatsapp",
        "com.discord",
    )

    /** 진단용: 결제와 관련 있을 만한 앱 (받은 횟수만 센다) */
    private val FINANCE_PACKAGE = Regex("bank|card|pay|messaging|mms|sms", RegexOption.IGNORE_CASE)

    private val AMOUNT = Regex("""\d[\d,]*\s*원""")
    private val PAYMENT_WORDS = Regex("승인|결제|취소|출금|입금|이체|사용")

    fun isKnownApp(packageName: String): Boolean = packageName in KNOWN_APPS

    fun isFinanceRelated(packageName: String): Boolean =
        packageName in KNOWN_APPS || packageName == NAVER_APP || packageName == KAKAOTALK ||
            FINANCE_PACKAGE.containsMatchIn(packageName)

    /** 수집해야 하면 걸린 규칙 이름을, 아니면 null을 돌려준다. */
    fun match(packageName: String, title: String?, text: String?, subText: String?): String? {
        if (packageName in KNOWN_APPS) return RULE_KNOWN_APP

        val content = listOfNotNull(title, text, subText).joinToString(" ")
        if (packageName == NAVER_APP) {
            return if (NAVER_PAYMENT_WORDS.containsMatchIn(content)) RULE_NAVER else null
        }
        if (!AMOUNT.containsMatchIn(content)) return null

        if (packageName == KAKAOTALK) {
            val sender = title.orEmpty()
            return if (KAKAOTALK_SENDERS.any { it in sender }) RULE_KAKAOTALK else null
        }
        if (packageName in MESSENGERS) return null

        // 모르는 앱이라도 금액과 결제 관련 단어가 같이 있으면 모아서 패키지명을 확인한다.
        return if (PAYMENT_WORDS.containsMatchIn(content)) RULE_AMOUNT else null
    }

    fun appName(packageName: String): String = KNOWN_APPS[packageName] ?: when (packageName) {
        KAKAOTALK -> "카카오톡"
        NAVER_APP -> "네이버"
        else -> packageName
    }
}
