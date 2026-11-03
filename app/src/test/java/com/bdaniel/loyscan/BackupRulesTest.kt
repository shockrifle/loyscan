package com.bdaniel.loyscan

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.di.CardPhotosModule
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/**
 * Card photos (#10) stay out of cloud backup: full-size pictures with their location don't go to the user's Google
 * account unasked, and don't use up its 25 MB quota. The rest of the backup rules are #71.
 */
@RunWith(AndroidJUnit4::class)
class BackupRulesTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val photos = "${CardPhotosModule.PHOTO_DIRECTORY}/"

    @Test
    fun cloudBackup_excludesCardPhotos() { // Android 12+
        assertThat(excludes(R.xml.data_extraction_rules, inside = "cloud-backup")).contains("file" to photos)
    }

    @Test
    fun deviceTransfer_keepsCardPhotos() { // Android 12+: a new phone gets them
        assertThat(excludes(R.xml.data_extraction_rules, inside = "device-transfer")).doesNotContain("file" to photos)
    }

    @Test
    fun theBackupRulesOfAndroid10And11_excludeCardPhotos() {
        assertThat(excludes(R.xml.backup_rules, inside = "full-backup-content")).contains("file" to photos)
    }

    /** The `(domain, path)` of every `<exclude>` directly inside the [inside] element of the rules file [xml]. */
    private fun excludes(xml: Int, inside: String): List<Pair<String, String>> {
        val parser = context.resources.getXml(xml)
        val found = mutableListOf<Pair<String, String>>()
        val open = ArrayDeque<String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "exclude" && open.lastOrNull() == inside) {
                        found += parser.getAttributeValue(null, "domain") to parser.getAttributeValue(null, "path")
                    }
                    open.addLast(parser.name)
                }
                XmlPullParser.END_TAG -> open.removeLast()
            }
        }
        return found
    }
}
