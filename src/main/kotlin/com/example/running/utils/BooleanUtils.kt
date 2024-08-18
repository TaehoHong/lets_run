package com.example.running.utils


inline fun Boolean.alsoIfTrue(block:() -> Unit): Boolean {

    if(this == true) {
        block()
    }

    return this
}