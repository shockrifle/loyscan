package com.bdaniel.loyscan.data.local.db

import com.bdaniel.loyscan.testing.exportedSchema
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * Every schema version from the first released one on is frozen (#12). KSP rewrites `app/schemas/…/<version>.json` from
 * the entities on every build, so a schema change made without bumping [AppDatabase.VERSION] changes a pinned hash
 * here, and a bump fails until its hash is pinned. Procedure: wiki/workflows/database-schema-changes.md.
 */
class FrozenSchemaTest {

    @Test
    fun everyVersionFromTheFirstRelease_isPinned() {
        assertWithMessage("Pin the identityHash of every schema version up to AppDatabase.VERSION in FROZEN_SCHEMAS")
            .that(FROZEN_SCHEMAS.keys)
            .containsExactlyElementsIn(FIRST_RELEASED_VERSION..AppDatabase.VERSION)
    }

    @Test
    fun everyFrozenSchema_isUnchanged() {
        FROZEN_SCHEMAS.forEach { (version, hash) ->
            assertWithMessage(
                "Schema $version changed in place. Revert the entity change, or bump AppDatabase.VERSION and add a " +
                    "migration (wiki/workflows/database-schema-changes.md)",
            ).that(exportedSchema(version).getValue("identityHash").jsonPrimitive.content).isEqualTo(hash)
        }
    }

    companion object {
        /** The schema of the first release. Versions before it never left development; their files are a record only. */
        const val FIRST_RELEASED_VERSION = 2

        /** Version → its exported `identityHash`. Add the new version's hash when you bump it; never edit one. */
        val FROZEN_SCHEMAS = mapOf(
            2 to "fe9f0878fb2710f56065c05fbf78cfc3",
            3 to "07e858e8e311b1797f964e04e48c4afa", // loyalty_cards.showPhotoOnList (#184)
            4 to "116a678dafe0922c3f385ecbc48ff1ad", // loyalty_cards.showPhotoOnWidget (#185)
        )
    }
}
