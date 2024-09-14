package com.example.running.user.enums

enum class AuthorityType(val code: Char, val role: String) {
    ADMIN('A', "ROLE_ADMIN"),
    USER('U', "ROLE_USER")
    ;

    companion object {

        fun get(code: Char): AuthorityType {
            return AuthorityType.entries
                .filter { it.code == code }
                .firstOrNull()
                ?: throw Exception("존재하지 않는 AuthorityType : $code")

        }

        fun get(role: String?): AuthorityType {
            return AuthorityType.entries
                .filter { it.role == role }
                .firstOrNull()
                ?: throw Exception("존재하지 않는 AuthorityType : $role")

        }
    }
}