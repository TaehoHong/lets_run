package com.example.running.utils

import kotlin.collections.*


inline fun Boolean.alsoIfTrue(block:() -> Unit) = if(this) block() else Unit

inline fun Boolean.alsoIfFalse(block:() -> Unit) = if(!this) block() else Unit

inline fun <C: Collection<*>> C.ifNotEmpty(defaultValue: () -> C): C {
    return if (isNotEmpty()) defaultValue() else this
}