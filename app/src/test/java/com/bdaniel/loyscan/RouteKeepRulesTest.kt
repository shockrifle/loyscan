package com.bdaniel.loyscan

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.serializer
import org.junit.Test

/**
 * Navigation finds an enum route argument's class by its serial name at runtime (`Class.forName`), so R8 must keep
 * that enum's name: in the minified app a renamed one crashes navigation to the route with "Cannot find class with
 * name …" (#73, found by `MinifiedAppSmokeTest`). This checks every route in `Navigation.kt` against
 * `proguard-rules.pro` on the JVM, so a new enum argument fails here rather than only on a device.
 */
class RouteKeepRulesTest {

    @Test
    fun theRoutesAreFoundInNavigation() {
        assertThat(routes().map { it.simpleName }).containsAtLeast("CardList", "Detail", "StorePicker", "CardForm")
    }

    @Test
    fun everyEnumRouteArgument_isKeptByName() {
        val rules = File("proguard-rules.pro").readLines().map { it.trim() }.filterNot { it.startsWith("#") }
        val enums = routes().flatMap { route -> serializer(route).descriptor.elementDescriptors }
            .filter { it.kind == SerialKind.ENUM }
            .map { it.serialName.removeSuffix("?") }
            .distinct()

        assertThat(enums).isNotEmpty()
        enums.forEach { enum ->
            assertWithMessage("proguard-rules.pro keeps the name of route argument $enum")
                .that(rules.any { it.matches(Regex("""-keep(names)?\s+enum\s+${Regex.escape(enum)}(\s.*)?""")) })
                .isTrue()
        }
    }

    /** Every `@Serializable` class or object declared in `Navigation.kt`: the app's routes. */
    private fun routes(): List<Class<*>> =
        Regex("""@Serializable\s+(?:data\s+)?(?:class|object)\s+(\w+)""")
            .findAll(File("src/main/java/com/bdaniel/loyscan/Navigation.kt").readText())
            .map { Class.forName("com.bdaniel.loyscan.${it.groupValues[1]}") }
            .toList()
}
