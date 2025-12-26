package com.example.running.domain.league.service

import org.springframework.stereotype.Component
import kotlin.random.Random

@Component
class BotNameGenerator {

    companion object {
        private val ADJECTIVES = listOf(
            "빠른", "꾸준한", "열정적인", "성실한", "즐거운",
            "활기찬", "용감한", "든든한", "행복한", "부지런한",
            "멋진", "씩씩한", "상쾌한", "튼튼한", "힘찬",
            "유쾌한", "건강한", "활발한", "당당한", "씽씽"
        )

        private val ANIMALS = listOf(
            "사슴", "거북이", "토끼", "치타", "호랑이",
            "독수리", "말", "사자", "늑대", "여우",
            "곰", "펭귄", "코끼리", "기린", "표범",
            "강아지", "고양이", "다람쥐", "원숭이", "돌고래"
        )
    }

    /**
     * 랜덤 봇 이름 생성
     */
    fun generate(): String {
        val adjective = ADJECTIVES[Random.nextInt(ADJECTIVES.size)]
        val animal = ANIMALS[Random.nextInt(ANIMALS.size)]
        return "$adjective$animal"
    }

    /**
     * 여러 개의 고유한 봇 이름 생성
     */
    fun generateUnique(count: Int): List<String> {
        val names = mutableSetOf<String>()
        val maxAttempts = count * 10 // 무한 루프 방지

        var attempts = 0
        while (names.size < count && attempts < maxAttempts) {
            names.add(generate())
            attempts++
        }

        // 부족한 경우 번호 추가
        var suffix = 1
        while (names.size < count) {
            val baseName = generate()
            names.add("$baseName$suffix")
            suffix++
        }

        return names.toList()
    }
}
