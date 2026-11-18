package com.bdaniel.loyscan.testing

import android.content.Context
import org.xmlpull.v1.XmlPullParser

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

/** Layer name (`background`, `foreground`, `monochrome`) → drawable resource id, from the `<adaptive-icon>` XML of [icon]. */
fun Context.adaptiveLayers(icon: Int): Map<String, Int> {
    val parser = resources.getXml(icon)
    val layers = mutableMapOf<String, Int>()
    while (parser.next() != XmlPullParser.END_DOCUMENT) {
        if (parser.eventType == XmlPullParser.START_TAG && parser.name != "adaptive-icon") {
            layers[parser.name] = parser.getAttributeResourceValue(ANDROID_NS, "drawable", 0)
        }
    }
    return layers
}
