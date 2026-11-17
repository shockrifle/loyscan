package com.bdaniel.loyscan.testing

/** The innermost cause. kotlinx.serialization 1.12 wrapped an exception thrown in a constructor while decoding; 1.13 doesn't. */
val Throwable.rootCause: Throwable get() = generateSequence(this) { it.cause }.last()
