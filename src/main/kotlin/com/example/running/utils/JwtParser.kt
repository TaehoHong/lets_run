package com.example.running.utils

import com.example.running.security.vo.AuthenticationVo
import org.springframework.security.core.context.SecurityContextHolder


fun getUserId() = (SecurityContextHolder.getContext().authentication.details as AuthenticationVo).id