package com.bdaniel.loyscan.testing

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/** Screens use the shared design values, not `.dp` literals or their own corner shapes (#108). */
class DesignValuesTest {

    @Test
    fun aDpLiteralInAModifier_isFound() {
        assertThat(designValueLiterals("val a = 1\nModifier.padding(16.dp)\n")).containsExactly(2)
    }

    @Test
    fun aNamedConstant_isNotFound() {
        assertThat(designValueLiterals("private val SELECTION_BORDER = 3.dp")).isEmpty()
        assertThat(designValueLiterals("val STORE_DOT_SMALL = 24.dp")).isEmpty()
    }

    @Test
    fun aLowerCaseVal_isNotANamedConstant() {
        assertThat(designValueLiterals("val gap = 8.dp")).containsExactly(1)
    }

    @Test
    fun zero_isNoDesignValue() {
        assertThat(designValueLiterals("width.coerceAtLeast(0.dp)")).isEmpty()
    }

    @Test
    fun aFractionalLiteral_isFound() {
        assertThat(designValueLiterals("Modifier.padding(0.5.dp)")).containsExactly(1)
    }

    @Test
    fun aRoundedCornerShape_isFound() {
        assertThat(designValueLiterals("Modifier.clip(RoundedCornerShape(8.dp))")).containsExactly(1)
        assertThat(designValueLiterals("Modifier.clip(RoundedCornerShape(percent = 6))")).containsExactly(1)
    }

    @Test
    fun commentsAndStrings_areSkipped() {
        val source = """
            |// 16.dp of padding, not a RoundedCornerShape(8.dp)
            |val s = "16.dp"
            |/* 8.dp */ Modifier.padding(Spacing.l)
        """.trimMargin()

        assertThat(designValueLiterals(source)).isEmpty()
    }

    @Test
    fun everyScreen_usesTheSharedDesignValues() {
        // The theme defines the values; everything else uses them.
        val files = File("src/main/java").walk()
            .filter { it.isFile && it.extension == "kt" && "ui/theme/" !in it.invariantSeparatorsPath }
            .toList()
        assertThat(files.map { it.name }).contains("ListScreen.kt") // the walk found the sources

        val literals = files.flatMap { file -> designValueLiterals(file.readText()).map { "${file.name}:$it" } }

        assertWithMessage("use Spacing, MaterialTheme.shapes or CardShape, or a named constant").that(literals).isEmpty()
    }
}
