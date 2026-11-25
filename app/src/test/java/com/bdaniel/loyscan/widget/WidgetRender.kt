package com.bdaniel.loyscan.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import kotlinx.coroutines.runBlocking
import org.robolectric.Shadows.shadowOf
import java.io.File

/**
 * The widget as a home screen draws it (#216): the real RemoteViews Glance makes for one layout size, inflated and
 * measured at a real size that may differ from it. The launcher picks the layout; the real size is the widget's frame.
 * A launcher may pick a layout wider than the widget (#108, Nova) or keep an old one (the 12L tablet), so a test can
 * show any layout at any width.
 *
 * Robolectric doesn't run the platform's collection adapter (`SetRemoteCollectionItemListAdapterAction` leaves the
 * grid's adapter `null`), so the grid gets an adapter over that action's own items here; the real `GridView` then
 * measures the real item views. Needs `@GraphicsMode(NATIVE)` for [RenderedWidget.savePng].
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class WidgetRender(private val context: Context, private val content: @Composable () -> Unit) {

    private val composed = mutableMapOf<Dp, RemoteViews>()

    /** The layout made for [layoutWidth], shown in a widget [realWidth] × [realHeight]. */
    fun show(layoutWidth: Dp, realWidth: Dp, realHeight: Dp = 1000.dp): RenderedWidget {
        val remoteViews = composed.getOrPut(layoutWidth) {
            runBlocking { GlanceRemoteViews().compose(context, DpSize(layoutWidth, WIDGET_LAYOUT_HEIGHT), content = content) }
                .remoteViews
        }
        val host = FrameLayout(context)
        host.addView(remoteViews.apply(context, host))
        findGrid(host)?.adapter = collectionAdapter(remoteViews)
        val density = context.resources.displayMetrics.density
        val width = (realWidth.value * density).toInt()
        val height = (realHeight.value * density).toInt()
        host.measure(exactly(width), exactly(height))
        host.layout(0, 0, width, height)
        shadowOf(Looper.getMainLooper()).idle()
        host.measure(exactly(width), exactly(height)) // the grid lays its items out on the second pass
        host.layout(0, 0, width, height)
        return RenderedWidget(host, density)
    }

    private fun collectionAdapter(remoteViews: RemoteViews): BaseAdapter? {
        val actions = RemoteViews::class.java.getDeclaredField("mActions").apply { isAccessible = true }
            .get(remoteViews) as List<*>? ?: return null
        val action = actions.firstOrNull { it?.javaClass?.simpleName == "SetRemoteCollectionItemListAdapterAction" }
            ?: return null
        val items = action.javaClass.getDeclaredField("mItems").apply { isAccessible = true }
            .get(action) as RemoteViews.RemoteCollectionItems
        return object : BaseAdapter() {
            override fun getCount() = items.itemCount
            override fun getItem(position: Int) = position
            override fun getItemId(position: Int) = items.getItemId(position)
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                items.getItemView(position).apply(context, parent)
        }
    }

    private fun findGrid(view: View): GridView? = when (view) {
        is GridView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findGrid(view.getChildAt(it)) }
        else -> null
    }

    private fun exactly(px: Int) = View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY)

    private companion object {
        /** The layout sizes' height ([WIDGET_SIZES]); only the width decides the layout. */
        val WIDGET_LAYOUT_HEIGHT = 40.dp
    }
}

/** A tile's bounds on the home screen, in dp. */
data class TileBounds(val description: String, val left: Float, val top: Float, val width: Float, val height: Float) {
    val bottom get() = top + height
}

/** A [WidgetRender] laid out at its real size. */
class RenderedWidget(private val host: FrameLayout, val density: Float) {

    /** Every tile, found by what TalkBack reads for it ([WidgetTile.description]), in grid order. */
    val tiles: List<TileBounds>
        get() = buildList { collectTiles(host, this) }

    /** One pixel in dp: the rounding a measured size can carry. */
    val pixel: Float get() = 1 / density

    /** Draws the widget into `app/build/outputs/widget-renders/[name].png`, for a person to look at. */
    fun savePng(name: String) {
        val bitmap = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
        host.draw(Canvas(bitmap))
        val file = File("build/outputs/widget-renders/$name.png").apply { parentFile?.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun collectTiles(view: View, into: MutableList<TileBounds>, left: Int = 0, top: Int = 0) {
        if (view.visibility != View.VISIBLE) return
        val x = left + view.left
        val y = top + view.top
        view.contentDescription?.let {
            into += TileBounds(it.toString(), x / density, y / density, view.width / density, view.height / density)
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) collectTiles(view.getChildAt(i), into, x, y)
    }
}
