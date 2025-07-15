package com.example.running.utils


inline fun Boolean.alsoIfTrue(block:() -> Unit) = if(this) block() else Unit

inline fun Boolean.alsoIfFalse(block:() -> Unit) = if(!this) block() else Unit