package com.example.running.user.enums

enum class AuthorityType(val code: Char) {
    ADMIN('A'),
    USER('U')
    ;

    companion object {

        fun get(code: Char): AuthorityType {
            return AuthorityType.entries
                .filter { it.code == code }
                .firstOrNull()
                ?: throw Exception("존재하지 않는 AuthorityType : $code")

        }
    }
}