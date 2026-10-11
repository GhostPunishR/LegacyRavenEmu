package com.ravenemu.app.achievements

import android.text.method.PasswordTransformationMethod
import android.view.View

/** Masks even the last typed character, regardless of Android's brief-reveal setting. */
internal object HiddenPasswordTransformation : PasswordTransformationMethod() {
    override fun getTransformation(source: CharSequence, view: View): CharSequence =
        MaskedPassword(source)
}

/** Live length is needed while EditText edits its backing Editable. */
internal class MaskedPassword(private val source: CharSequence) : CharSequence {
    override val length: Int get() = source.length
    override fun get(index: Int): Char {
        if (index !in 0 until length) throw IndexOutOfBoundsException()
        return '\u2022'
    }
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        if (startIndex < 0 || endIndex < startIndex || endIndex > length) throw IndexOutOfBoundsException()
        return "\u2022".repeat(endIndex - startIndex)
    }
    override fun toString(): String = "\u2022".repeat(length)
}
