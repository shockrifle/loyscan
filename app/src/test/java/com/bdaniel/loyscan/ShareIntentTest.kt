package com.bdaniel.loyscan

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** What LoyScan accepts as a shared picture (#77): the manifest's filter, and the intent it then reads. */
@RunWith(AndroidJUnit4::class)
class ShareIntentTest {

    private val packageManager = ApplicationProvider.getApplicationContext<android.content.Context>().packageManager

    @Test
    fun sharingOnePicture_resolvesToLoyScan() {
        for (type in listOf("image/png", "image/jpeg", "image/*")) {
            assertThat(resolves(Intent(Intent.ACTION_SEND).setType(type))).isTrue()
        }
    }

    @Test
    fun sharingOnePicture_alsoResolvesToSetAsStoreLogo() { // #151: a second share target
        for (type in listOf("image/png", "image/jpeg", "image/*")) {
            assertThat(resolves(Intent(Intent.ACTION_SEND).setType(type), SHARE_AS_STORE_LOGO)).isTrue()
        }
        assertThat(resolves(Intent(Intent.ACTION_SEND).setType("text/plain"), SHARE_AS_STORE_LOGO)).isFalse()
    }

    @Test
    fun aShareThroughSetAsStoreLogo_sharesAsAStoreLogo_andThePlainShareDoesNot() { // #151
        val asLogo = share().setComponent(ComponentName(context, SHARE_AS_STORE_LOGO))

        assertThat(asLogo.sharesAsStoreLogo()).isTrue()
        assertThat(asLogo.sharedPicture()).isEqualTo(Uri.parse(PICTURE))
        assertThat(share().sharesAsStoreLogo()).isFalse()
        assertThat(share().setComponent(ComponentName(context, ShareActivity::class.java)).sharesAsStoreLogo()).isFalse()
    }

    @Test
    fun aStoreLogoShareForwardedToALoyScanOfItsOwn_isStillAStoreLogoShare() { // #151, as #83
        val started = mutableListOf<Intent>()

        share().setComponent(ComponentName(context, SHARE_AS_STORE_LOGO)).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .forwardToATaskOfItsOwn(context, start = started::add)

        assertThat(started.single().sharesAsStoreLogo()).isTrue()
        assertThat(started.single().sharedPicture()).isEqualTo(Uri.parse(PICTURE))
    }

    @Test
    fun sharingAnythingElse_doesNotResolveToLoyScan() {
        assertThat(resolves(Intent(Intent.ACTION_SEND).setType("text/plain"))).isFalse()
        assertThat(resolves(Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/png"))).isFalse()
        assertThat(resolves(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(PICTURE), "image/png"))).isFalse()
    }

    @Test
    fun bothActivities_useTheStandardLaunchMode() { // #83: no singleTop, so a share never replaces an open screen
        for (activity in listOf(MainActivity::class.java, ShareActivity::class.java)) {
            assertThat(packageManager.getActivityInfo(ComponentName(context, activity), 0).launchMode)
                .isEqualTo(ActivityInfo.LAUNCH_MULTIPLE)
        }
    }

    @Test
    fun aPictureShare_startsTheShareActivity_neverTheLaunchersMainActivity() { // #189
        val shared = Intent(Intent.ACTION_SEND).setType("image/png")

        assertThat(resolves(shared, ShareActivity::class.java.name)).isTrue()
        assertThat(resolves(shared, MainActivity::class.java.name)).isFalse()
    }

    @Test
    fun theLauncher_startsMainActivity_neverTheShareActivity() { // #189
        // A launcher's filter has no DEFAULT category, so it is queried without MATCH_DEFAULT_ONLY.
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        assertThat(resolves(launcher, MainActivity::class.java.name, flags = 0)).isTrue()
        assertThat(resolves(launcher, ShareActivity::class.java.name, flags = 0)).isFalse()
    }

    @Test
    fun theShareActivity_hasNoTaskAffinity_soNeitherTheLauncherNorTheWidgetReachesAShareTask() { // #189
        // Seen on a device: with LoyScan's affinity, the launcher stacked a second LoyScan on a share left open, and
        // Back walked through the old share flow; a widget tap opened its card there.
        val share = packageManager.getActivityInfo(ComponentName(context, ShareActivity::class.java), 0)
        val main = packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)

        assertThat(share.taskAffinity.orEmpty()).isEmpty()
        assertThat(main.taskAffinity).isEqualTo(context.packageName)
    }

    @Test
    fun everyShare_startsATaskOfItsOwn_whateverFlagsItsSenderSets() { // #189
        // Seen on a device: a share sent with NEW_TASK alone stacked on a share left open, and Back walked its flow.
        val share = packageManager.getActivityInfo(ComponentName(context, ShareActivity::class.java), 0)

        assertThat(share.documentLaunchMode).isEqualTo(ActivityInfo.DOCUMENT_LAUNCH_ALWAYS)
    }

    @Test
    fun setAsStoreLogo_isAnAliasOfTheShareActivity() { // #151, #189: a logo share runs in a share task too
        val alias = packageManager.getActivityInfo(ComponentName(context, SHARE_AS_STORE_LOGO), 0)

        assertThat(alias.targetActivity).isEqualTo(ShareActivity::class.java.name)
    }

    @Test
    fun aShareForwardedToALoyScanOfItsOwn_isTheSameShare_inANewTask_withTheReadGrantPassedOn() { // #83
        val started = mutableListOf<Intent>()

        share().addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).forwardToATaskOfItsOwn(context, start = started::add)

        val forwarded = started.single()
        assertThat(forwarded.component?.className).isEqualTo(ShareActivity::class.java.name)
        assertThat(forwarded.sharedPicture()).isEqualTo(Uri.parse(PICTURE))
        assertThat(forwarded.flags).isEqualTo(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }

    @Test
    fun aForwardedShareLoyScanHasNoGrantFor_isDropped_insteadOfCrashing() { // seen on a device: SecurityException
        share().forwardToATaskOfItsOwn(context, start = { throw SecurityException("no permission to $PICTURE") })
    }

    @Test
    fun aSharedPicture_isItsContentUri() {
        assertThat(share().sharedPicture()).isEqualTo(Uri.parse(PICTURE))
    }

    @Test
    fun aShareOfAnythingElse_isNoPicture() {
        assertThat(share(type = "text/plain").sharedPicture()).isNull()
        assertThat(share().setAction(Intent.ACTION_SEND_MULTIPLE).sharedPicture()).isNull()
        assertThat(Intent(Intent.ACTION_MAIN).sharedPicture()).isNull()
        assertThat(Intent(Intent.ACTION_SEND).setType("image/png").sharedPicture()).isNull() // no picture attached
    }

    @Test
    fun aFileUri_isNoPicture() { // only a content:// URI granted with the share; a file path could be the app's own
        assertThat(share(uri = "file:///data/data/com.bdaniel.loyscan/databases/loyscan.db").sharedPicture()).isNull()
    }

    @Test
    fun aShareReopenedFromRecents_isNoPicture() { // it was handled when it arrived
        val reopened = share().addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

        assertThat(reopened.sharedPicture()).isNull()
    }

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun share(type: String = "image/png", uri: String = PICTURE) =
        Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))

    private fun resolves(
        intent: Intent,
        activity: String = ShareActivity::class.java.name,
        flags: Int = PackageManager.MATCH_DEFAULT_ONLY,
    ) =
        packageManager.queryIntentActivities(intent.setPackage(context.packageName), flags)
            .any { it.activityInfo.name == activity }
}

private const val PICTURE = "content://media/picker/0/42"
