package com.example.running.domain.common.entity.converter

import com.example.running.domain.common.enums.AuthorityType
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter
class AuthorityTypeConverter : AttributeConverter<AuthorityType, Char> {

    override fun convertToDatabaseColumn(authorityType: AuthorityType?): Char {
        return authorityType?.code ?: throw Exception("authorityType must not be null")
    }

    override fun convertToEntityAttribute(dbData: Char?): AuthorityType? {
        return dbData?.let { AuthorityType.get(it) }
    }
}