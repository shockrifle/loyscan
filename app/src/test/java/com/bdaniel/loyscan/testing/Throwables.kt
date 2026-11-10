package com.bdaniel.loyscan.testing

/** The innermost cause: kotlinx.serialization 1.12+ wraps an exception thrown in a constructor while decoding. */
val Throwable.rootCause: Throwable get() = generateSequence(this) { it.cause }.last()
