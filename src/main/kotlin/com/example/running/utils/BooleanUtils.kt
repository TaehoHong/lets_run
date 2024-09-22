package com.example.running.utils


inline fun Boolean.alsoIfTrue(block:() -> Unit) = if(this) block() else this

inline fun Boolean.alsoIfFalse(block:() -> Unit) = if(!this) block() else this