package com.example.running.utils


inline fun Boolean.alsoIfTrue(block:() -> Unit) = if(this) block() else Unit

inline fun Boolean.alsoIfFalse(block:() -> Unit) = if(!this) block() else Unit

inline fun <C: Collection<*>> C.ifNotEmpty(defaultValue: (C) -> Unit)  {
    if (isNotEmpty()) defaultValue(this)
}

inline fun <T> T.alsoIf(predicate: (T) -> Boolean, block: (T) -> Unit): T {
    if(predicate(this)) block(this)
    return this
}