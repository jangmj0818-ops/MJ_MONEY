package com.mj.money.notification.parse

/** 알림이 가리키는 결제수단의 단서 (실제 결제수단은 사용자의 결제수단 목록에서 찾는다) */
enum class MethodKind { KAKAOBANK_CARD, KAKAOBANK_ACCOUNT, KAKAOPAY, NAVERPAY, SAMSUNG_CARD, NONE }

data class MethodHint(val kind: MethodKind, val last4: String? = null) {
    companion object {
        val NONE = MethodHint(MethodKind.NONE)
    }
}

enum class Direction { EXPENSE, INCOME, CANCEL }

sealed interface ParseOutcome {
    /** 기록하지 않는 알림 (내 계좌 간 이동, 카드값, 안내 등) */
    data class Ignore(val reason: String) : ParseOutcome

    /** 돈이 오간 알림 */
    data class Payment(
        val direction: Direction,
        val amount: Long,
        val merchant: String?,
        val method: MethodHint,
        /** 카카오페이·네이버페이처럼 거쳐 간 페이 */
        val via: MethodHint? = null,
        /** 알림만으로 알 수 있는 카테고리 이름 (예: 티머니 → 교통) */
        val categoryHint: String? = null,
    ) : ParseOutcome

    /** 금액은 없고 가맹점·상품만 있는 주문 알림 (다른 알림의 가맹점 이름을 채우는 데 쓴다) */
    data class MerchantOnly(val merchant: String, val item: String?) : ParseOutcome
}

/**
 * 결제 알림 해석기. 실제 폰에 온 알림 형식(2026-09 ~ 10월 수집)을 바탕으로 만들었다.
 * 형식이 바뀌어 해석하지 못하면 Ignore가 되고, 원문은 로그에 남아 있으니 규칙을 고쳐 다시 처리할 수 있다.
 */
object PaymentParser {

    private const val KAKAOBANK_APP = "com.kakaobank.channel"
    private const val KAKAOTALK = "com.kakao.talk"
    private const val NAVER_APP = "com.nhn.android.search"
    private const val NAVERPAY_APP = "com.naverfin.payapp"
    private const val KAKAOPAY_APP = "com.kakaopay.app"
    private val SAMSUNG_CARD_APPS = setOf("net.ib.android.smcard", "kr.co.samsungcard.mpocket")
    private const val TMONEY_APP = "com.lgt.tmoney"
    private const val MESSAGES_APP = "com.samsung.android.messaging"
    private val BANK_APPS_IGNORED = setOf("com.kbstar.kbbank", "com.hanaskcard.paycla")

    private val AMOUNT = Regex("""(\d[\d,]*)\s*원""")

    // 카카오뱅크
    private val KB_CARD_TITLE = Regex("""^카드 결제( 취소)? ([\d,]+)원$""")
    private val KB_CARD_TEXT = Regex("""^체크카드\((\d{4})\)\s*\|\s*(.+)$""")
    private val KB_MOVE_TITLE = Regex("""^(?:(.+?)\s)?(출금|입금) ([\d,]+)원$""")
    private val KB_OUT_TEXT = Regex("""^(.+?)\((\d{4})\)\s*→\s*(.+)$""")
    private val KB_IN_TEXT = Regex("""^(.+?)\s*→\s*(.+?)\((\d{4})\)$""")

    // 네이버페이
    private val NAVER_PAID = Regex("""^(.+?)에서 ([\d,]+)원 결제되었습니다\.?(?:\s*\(결제 수단:\s*(.+?)\))?""")
    private val NAVER_CANCELED = Regex("""^(.+?)에서 ([\d,]+)원 (?:결제가 )?취소되었습니다""")
    private val NAVER_ORDER = Regex("""^(.+?)\s+상품이 결제되었습니다""")

    // 문자: 네이버 멤버십 등 "[네이버 멤버십] 서비스 이름 7,900원 결제"
    private val SMS_MEMBERSHIP = Regex("""\[네이버\s?(?:플러스\s?)?멤버십\]\s*(.+?)\s+([\d,]+)원\s*결제""")

    // 카드 승인 문자·알림의 "MM/dd HH:mm 가맹점" 꼴
    private val CARD_TIME_MERCHANT = Regex("""\d{2}/\d{2}\s+\d{2}:\d{2}\s+(.+)""")

    /** 내 계좌끼리 옮긴 돈으로 보는 받는 곳·보낸 곳 */
    private val INTERNAL_ACCOUNTS = Regex("세이프박스|모임통장|저금통|기록통장|입출금통장")

    /** 카드값 납부로 보는 받는 곳 (카드로 결제할 때 이미 기록되니 두 번 세지 않는다) */
    private val CARD_BILL = Regex("""카드(결제|대금)?$|카드대금|카드출금""")

    fun parse(packageName: String, title: String?, text: String?, subText: String?): ParseOutcome {
        val t = title?.trim().orEmpty()
        val body = text?.trim().orEmpty()
        return when {
            packageName == KAKAOBANK_APP -> parseKakaoBank(t, body)
            packageName == KAKAOTALK -> parseKakaoTalk(t, body)
            packageName == NAVER_APP || packageName == NAVERPAY_APP -> parseNaverPay(t, body)
            packageName == KAKAOPAY_APP -> parseGenericPay(t, body, MethodHint(MethodKind.KAKAOPAY))
            packageName in SAMSUNG_CARD_APPS -> parseCardApproval("$t $body", MethodHint(MethodKind.SAMSUNG_CARD))
            packageName == TMONEY_APP -> parseTmoney(body)
            packageName == MESSAGES_APP -> parseSms(body)
            packageName in BANK_APPS_IGNORED -> ParseOutcome.Ignore("은행 계좌 이동은 기록하지 않음")
            else -> ParseOutcome.Ignore("해석 규칙이 없는 앱")
        }
    }

    // ---------- 카카오뱅크 ----------

    private fun parseKakaoBank(title: String, text: String): ParseOutcome {
        KB_CARD_TITLE.matchEntire(title)?.let { m ->
            val amount = m.groupValues[2].toAmount()
            val card = KB_CARD_TEXT.matchEntire(text)
            val hint = MethodHint(MethodKind.KAKAOBANK_CARD, card?.groupValues?.get(1))
            val merchant = card?.groupValues?.get(2)?.trim()
            val direction = if (m.groupValues[1].isNotEmpty()) Direction.CANCEL else Direction.EXPENSE
            return ParseOutcome.Payment(direction, amount, merchant, hint)
        }
        KB_MOVE_TITLE.matchEntire(title)?.let { m ->
            return parseKakaoBankMove(account = m.groupValues[1], kind = m.groupValues[2], amount = m.groupValues[3].toAmount(), text = text)
        }
        return ParseOutcome.Ignore("카카오뱅크 안내·혜택 알림")
    }

    /** 카카오뱅크 앱과 카카오톡 알림톡이 같은 형식의 출금·입금을 보낸다. */
    private fun parseKakaoBankMove(account: String, kind: String, amount: Long, text: String): ParseOutcome {
        if (account.contains("모임통장")) return ParseOutcome.Ignore("모임통장 거래")
        if (amount <= 0) return ParseOutcome.Ignore("0원")
        return if (kind == "출금") {
            val m = KB_OUT_TEXT.matchEntire(text) ?: return ParseOutcome.Ignore("출금 형식을 알 수 없음")
            val from = m.groupValues[1]
            val last4 = m.groupValues[2]
            val to = m.groupValues[3].trim()
            when {
                from.contains("모임통장") || INTERNAL_ACCOUNTS.containsMatchIn(to) -> ParseOutcome.Ignore("내 계좌 간 이동")
                to.contains("충전") -> ParseOutcome.Ignore("페이 충전")
                CARD_BILL.containsMatchIn(to) -> ParseOutcome.Ignore("카드값 납부")
                to == "카카오페이" -> ParseOutcome.Payment(Direction.EXPENSE, amount, null, MethodHint(MethodKind.KAKAOPAY))
                to == "네이버페이" -> ParseOutcome.Payment(Direction.EXPENSE, amount, null, MethodHint(MethodKind.NAVERPAY))
                // 토스페이·가맹점·사람에게 보낸 돈: 카카오뱅크 계좌에서 나간 지출로 본다. (체크카드 알림과 겹치면 합쳐진다)
                else -> ParseOutcome.Payment(Direction.EXPENSE, amount, to, MethodHint(MethodKind.KAKAOBANK_ACCOUNT, last4))
            }
        } else {
            val m = KB_IN_TEXT.matchEntire(text) ?: return ParseOutcome.Ignore("입금 형식을 알 수 없음")
            val from = m.groupValues[1].trim()
            val to = m.groupValues[2]
            when {
                to.contains("모임통장") || INTERNAL_ACCOUNTS.containsMatchIn(from) -> ParseOutcome.Ignore("내 계좌 간 이동")
                else -> ParseOutcome.Payment(Direction.INCOME, amount, from, MethodHint(MethodKind.KAKAOBANK_ACCOUNT, m.groupValues[3]))
            }
        }
    }

    // ---------- 카카오톡 알림톡 ----------

    private fun parseKakaoTalk(title: String, text: String): ParseOutcome {
        return when {
            title.contains("카카오뱅크") -> {
                // "10/01 12:19\n출금 21,500원\n입출금통장(1234) → 카카오페이"
                val lines = text.lines().map(String::trim).filter(String::isNotEmpty)
                val head = lines.firstNotNullOfOrNull { KB_MOVE_TITLE.matchEntire(it) }
                    ?: return ParseOutcome.Ignore("카카오뱅크 알림톡 안내")
                val detail = lines.lastOrNull { it.contains("→") } ?: return ParseOutcome.Ignore("알림톡 형식을 알 수 없음")
                parseKakaoBankMove(head.groupValues[1], head.groupValues[2], head.groupValues[3].toAmount(), detail)
            }
            title.contains("삼성카드") -> parseCardApproval(text, MethodHint(MethodKind.SAMSUNG_CARD))
            title.contains("카카오페이") -> parseGenericPay(title, text, MethodHint(MethodKind.KAKAOPAY))
            title.contains("네이버페이") -> parseNaverPay("네이버페이", text)
            else -> ParseOutcome.Ignore("결제 채널이 아닌 카카오톡")
        }
    }

    // ---------- 네이버페이 (네이버 앱·네이버페이 앱) ----------

    private fun parseNaverPay(title: String, text: String): ParseOutcome {
        NAVER_CANCELED.find(text)?.let { m ->
            return ParseOutcome.Payment(Direction.CANCEL, m.groupValues[2].toAmount(), m.groupValues[1].trim(), MethodHint(MethodKind.NAVERPAY))
        }
        NAVER_PAID.find(text)?.let { m ->
            val amount = m.groupValues[2].toAmount()
            if (amount <= 0) return ParseOutcome.Ignore("0원")
            return ParseOutcome.Payment(
                direction = Direction.EXPENSE,
                amount = amount,
                merchant = m.groupValues[1].trim(),
                method = MethodHint(MethodKind.NAVERPAY),
                via = MethodHint(MethodKind.NAVERPAY),
            )
        }
        NAVER_ORDER.find(text)?.let { m ->
            if (title.isNotEmpty() && title != "네이버페이") {
                return ParseOutcome.MerchantOnly(merchant = title, item = m.groupValues[1].trim().trimEnd('…', '.').trim())
            }
        }
        return ParseOutcome.Ignore("네이버 결제 안내·적립 알림")
    }

    // ---------- 그 밖의 페이·카드 ----------

    private fun parseGenericPay(title: String, text: String, hint: MethodHint): ParseOutcome {
        val content = "$title $text"
        val amount = AMOUNT.find(content)?.groupValues?.get(1)?.toAmount() ?: return ParseOutcome.Ignore("금액 없음")
        if (amount <= 0) return ParseOutcome.Ignore("0원")
        return when {
            content.contains("취소") || content.contains("환불") -> ParseOutcome.Payment(Direction.CANCEL, amount, null, hint)
            content.contains("결제") && !content.contains("예정") && !content.contains("안내") ->
                ParseOutcome.Payment(Direction.EXPENSE, amount, null, hint, via = hint)
            else -> ParseOutcome.Ignore("결제가 아닌 페이 알림")
        }
    }

    /** 카드 승인 알림: "… 승인 … 12,000원 일시불 10/04 12:30 가맹점" */
    private fun parseCardApproval(content: String, hint: MethodHint): ParseOutcome {
        val amount = AMOUNT.find(content)?.groupValues?.get(1)?.toAmount() ?: return ParseOutcome.Ignore("금액 없음")
        if (amount <= 0) return ParseOutcome.Ignore("0원")
        val merchant = CARD_TIME_MERCHANT.find(content)?.groupValues?.get(1)
            ?.substringBefore("누적")?.substringBefore("잔여")?.trim()?.takeIf(String::isNotEmpty)
        return when {
            content.contains("승인") && content.contains("취소") -> ParseOutcome.Payment(Direction.CANCEL, amount, merchant, hint)
            content.contains("승인") -> ParseOutcome.Payment(Direction.EXPENSE, amount, merchant, hint)
            else -> ParseOutcome.Ignore("승인이 아닌 카드 안내")
        }
    }

    private fun parseTmoney(text: String): ParseOutcome {
        val amount = AMOUNT.find(text)?.groupValues?.get(1)?.toAmount() ?: return ParseOutcome.Ignore("금액 없음")
        if (amount <= 0) return ParseOutcome.Ignore("0원")
        if (!text.contains("결제")) return ParseOutcome.Ignore("티머니 안내")
        return ParseOutcome.Payment(Direction.EXPENSE, amount, "티머니", MethodHint.NONE, categoryHint = "교통")
    }

    private fun parseSms(text: String): ParseOutcome {
        SMS_MEMBERSHIP.find(text)?.let { m ->
            return ParseOutcome.Payment(
                Direction.EXPENSE, m.groupValues[2].toAmount(), m.groupValues[1].trim(),
                MethodHint(MethodKind.NAVERPAY), via = MethodHint(MethodKind.NAVERPAY),
            )
        }
        if (text.contains("삼성") && text.contains("승인")) return parseCardApproval(text, MethodHint(MethodKind.SAMSUNG_CARD))
        return ParseOutcome.Ignore("결제 문자가 아님")
    }

    private fun String.toAmount(): Long = replace(",", "").toLongOrNull() ?: 0L
}
