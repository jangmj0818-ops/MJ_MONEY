package com.mj.money.domain

/** 가맹점 이름 비교: "(주)", "주식회사", 공백, 대소문자 차이는 무시한다. */
fun normalizeMerchant(name: String): String =
    name.replace(Regex("""\(주\)|㈜|주식회사|\s+"""), "").lowercase()

/**
 * 학습한 가맹점 중에서 찾는다. 이름이 같으면 그것을, 아니면 한쪽이 다른 쪽으로 시작하는 것을 쓴다.
 * (카카오뱅크는 가맹점 이름을 잘라서 보내서 "테스트낙지광화문세"처럼 앞부분만 오기도 한다)
 */
fun <T> findMerchantRule(merchant: String, rules: Map<String, T>): T? {
    val key = normalizeMerchant(merchant)
    if (key.length < 2) return null
    rules[key]?.let { return it }
    return rules.entries
        .filter { (ruleKey, _) -> minOf(ruleKey.length, key.length) >= 4 && (ruleKey.startsWith(key) || key.startsWith(ruleKey)) }
        .maxByOrNull { it.key.length }
        ?.value
}

/** 처음 보는 가맹점이라도 이름으로 짐작할 수 있는 카테고리 (확실한 것만) */
private val BUILT_IN_HINTS: List<Pair<Regex, String>> = listOf(
    Regex("우아한형제들|배달의민족|요기요|쿠팡이츠") to "식비",
    Regex("스타벅스|투썸|이디야|메가커피|메가엠지씨|컴포즈|빽다방|폴바셋|커피|카페|파리바게|뚜레쥬르|베이커리|아이스크림|배스킨") to "카페·간식",
    Regex("티머니|택시|카카오T|코레일|지하철|버스|주유|주차") to "교통",
    Regex("넷플릭스|유튜브|티빙|웨이브|왓챠|디즈니|멜론|스포티파이") to "OTT·구독",
    Regex("openai|chatgpt|anthropic|claude", RegexOption.IGNORE_CASE) to "AI",
)

fun builtInCategoryHint(merchant: String): String? =
    BUILT_IN_HINTS.firstOrNull { (pattern, _) -> pattern.containsMatchIn(merchant) }?.second

/** 가맹점 이름이 정보가 없는 것 (페이 이름뿐인 경우) */
fun isGenericMerchant(merchant: String?): Boolean =
    merchant.isNullOrBlank() || normalizeMerchant(merchant) in setOf("토스페이", "카카오페이", "네이버페이", "티머니")

/** 두 알림의 가맹점 이름 중 더 쓸모 있는 것: 정보가 있는 쪽, 둘 다 있으면 긴 쪽(잘리지 않은 이름) */
fun betterMerchant(a: String?, b: String?): String? = when {
    isGenericMerchant(a) && isGenericMerchant(b) -> a ?: b
    isGenericMerchant(a) -> b
    isGenericMerchant(b) -> a
    else -> if (b!!.length > a!!.length) b else a
}
