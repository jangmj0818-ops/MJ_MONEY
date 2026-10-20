package com.mj.money.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantMatchingTest {

    @Test
    fun `가맹점 이름 정리 - 주식회사 표기, 공백, 대소문자 무시`() {
        assertEquals("테스트상회", normalizeMerchant("(주) 테스트 상회"))
        assertEquals("테스트상회", normalizeMerchant("주식회사 테스트상회"))
        assertEquals("abcmart", normalizeMerchant("ABC Mart"))
    }

    @Test
    fun `학습한 가맹점 찾기 - 잘린 이름도 앞부분이 같으면 같은 가게`() {
        val rules = mapOf(normalizeMerchant("테스트낙지 광화문세종점") to 1L)
        assertEquals(1L, findMerchantRule("테스트낙지 광화문세종점", rules))
        assertEquals(1L, findMerchantRule("테스트낙지 광화문세", rules)) // 카카오뱅크가 잘라서 보낸 이름
        assertNull(findMerchantRule("다른가게", rules))
        assertNull(findMerchantRule("테", rules)) // 너무 짧으면 짐작하지 않는다
    }

    @Test
    fun `이름으로 짐작하는 카테고리`() {
        assertEquals("카페·간식", builtInCategoryHint("테스트커피 역삼점"))
        assertEquals("식비", builtInCategoryHint("(주)우아한형제들"))
        assertEquals("교통", builtInCategoryHint("티머니"))
        assertNull(builtInCategoryHint("테스트상회"))
    }

    @Test
    fun `더 쓸모 있는 가맹점 이름 고르기`() {
        assertEquals("테스트식당", betterMerchant("토스페이", "테스트식당"))
        assertEquals("테스트낙지 광화문세종점", betterMerchant("테스트낙지 광화문세", "테스트낙지 광화문세종점"))
        assertEquals("테스트식당", betterMerchant("테스트식당", null))
        assertTrue(isGenericMerchant("카카오페이"))
        assertFalse(isGenericMerchant("테스트식당"))
    }
}
