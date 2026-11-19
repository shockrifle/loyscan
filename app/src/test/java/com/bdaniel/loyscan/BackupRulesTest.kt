package com.bdaniel.loyscan

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

private const val ANDROID = "http://schemas.android.com/apk/res/android"

/**
 * Cloud backup carries no app data (#71): the user's cards, their photos (#10) and preferences don't go to the user's
 * Google account unasked, and a `loyscan.db` restored from the cloud at install can't crash the app on a schema it
 * doesn't match ([[no-room-migrations]], #12; device-to-device transfer still carries the db, so it keeps that path
 * open). Device-to-device transfer to a new phone still carries all of it. Opt-in cloud backup is #24, after v1.
 *
 * The app targets API 36, so Android 12+ reads `data_extraction_rules.xml` and Android 10–11 (minSdk 29) reads
 * `backup_rules.xml`. The two formats express "nothing to the cloud, everything to a new phone" differently:
 * 12+ has independent `<cloud-backup>` / `<device-transfer>` sections; 10–11 has one rule set, so device-only content
 * is an `<include requireFlags="deviceToDeviceTransfer">`, which also makes the whole file include-only.
 */
@RunWith(AndroidJUnit4::class)
class BackupRulesTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /**
     * Every domain an `<include>`/`<exclude>` can name (https://developer.android.com/identity/data/autobackup#xml-include-exclude).
     * Excluding all of them leaves the cloud backup empty whatever the app writes; including all of them (D2D-gated, on
     * 10–11) carries all of it to a new phone. The app writes only in `file`/`database` today, but pinning the full set
     * keeps a future file written outside them from leaking to the cloud or being left behind on transfer.
     */
    private val allDomains = setOf(
        "root", "file", "database", "sharedpref", "external",
        "device_root", "device_file", "device_database", "device_sharedpref",
    )

    @Test
    fun theManifest_allowsBackup_andPointsAndroidAtBothRulesFiles() { // #75: without them Android backs up everything
        // The rules files below mean nothing unless the manifest names them; with allowBackup false, a new phone would get
        // nothing either. Read from the app's manifest: a library's can't change these without tools:replace.
        val application = manifestApplication()

        assertThat(application["allowBackup"]).isEqualTo("true")
        assertThat(application["dataExtractionRules"]).isEqualTo("@xml/data_extraction_rules")
        assertThat(application["fullBackupContent"]).isEqualTo("@xml/backup_rules")
    }

    // --- Android 12+ : data_extraction_rules.xml ---

    @Test
    fun cloudBackup_excludesEveryDataDomain() { // nothing reaches the user's Google account
        val excludedDomains = rules(R.xml.data_extraction_rules, inside = "cloud-backup", tag = "exclude")
            .filter { it.path == "." }
            .map { it.domain }
            .toSet()
        assertThat(excludedDomains).containsAtLeastElementsIn(allDomains)
    }

    @Test
    fun cloudBackup_includesNothing() { // an <include> would re-enable cloud backup for whatever it names
        assertThat(rules(R.xml.data_extraction_rules, inside = "cloud-backup", tag = "include")).isEmpty()
    }

    @Test
    fun deviceTransfer_carriesDatabasePrefsAndPhotos() { // a new phone gets everything
        // No <device-transfer> section (or one that excludes nothing) means D2D transfers all app data: the Room
        // database, the DataStore preferences and the card photos (all under the file/database domains).
        val excluded = rules(R.xml.data_extraction_rules, inside = "device-transfer", tag = "exclude").map { it.domain }
        assertThat(excluded).containsNoneOf("file", "database")
    }

    // --- Android 10–11 : backup_rules.xml (one rule set for cloud and D2D) ---

    @Test
    fun legacyRules_areIncludeOnlyAndEveryIncludeIsDeviceToDeviceOnly() {
        // Any <include> makes the file include-only (the default "back up everything" is dropped). Gating every include
        // with deviceToDeviceTransfer means the cloud transfer, which doesn't set that flag, is left with nothing.
        val includes = rules(R.xml.backup_rules, inside = "full-backup-content", tag = "include")
        assertThat(includes).isNotEmpty()
        assertThat(includes.all { it.requireFlags?.contains("deviceToDeviceTransfer") == true }).isTrue()
    }

    @Test
    fun legacyRules_transferEveryDomainToANewPhone() {
        // Photos + DataStore prefs live in `file`, the Room db in `database`; carrying every domain matches 12+, where
        // the absent <device-transfer> section carries everything.
        val d2dDomains = rules(R.xml.backup_rules, inside = "full-backup-content", tag = "include")
            .filter { it.requireFlags?.contains("deviceToDeviceTransfer") == true && it.path == "." }
            .map { it.domain }
            .toSet()
        assertThat(d2dDomains).containsAtLeastElementsIn(allDomains)
    }

    /** The `android:` attributes of the app manifest's `<application>`, by local name. */
    private fun manifestApplication(): Map<String, String> {
        val application = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
            .getElementsByTagName("application").item(0)
        val attributes = application.attributes
        return (0 until attributes.length).map { attributes.item(it) }
            .filter { it.namespaceURI == ANDROID }
            .associate { it.localName to it.nodeValue }
    }

    private data class Rule(val domain: String?, val path: String?, val requireFlags: String?)

    /** Every [tag] element (`include` / `exclude`) directly inside the [inside] element of the rules file [xml]. */
    private fun rules(xml: Int, inside: String, tag: String): List<Rule> {
        val parser = context.resources.getXml(xml)
        val found = mutableListOf<Rule>()
        val open = ArrayDeque<String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == tag && open.lastOrNull() == inside) {
                        found += Rule(
                            domain = parser.getAttributeValue(null, "domain"),
                            path = parser.getAttributeValue(null, "path"),
                            requireFlags = parser.getAttributeValue(null, "requireFlags"),
                        )
                    }
                    open.addLast(parser.name)
                }
                XmlPullParser.END_TAG -> open.removeLast()
            }
        }
        return found
    }
}
