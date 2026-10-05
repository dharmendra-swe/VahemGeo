package com.vahemgeo.app.engine

import android.util.Xml
import org.osmdroid.util.GeoPoint
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

object GpxParser {
    /**
     * Parses a GPX stream and extracts a list of GeoPoints from <trkpt>, <rtept>, or <wpt> nodes.
     */
    fun parseGpx(inputStream: InputStream): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                val tagName = parser.name
                if (tagName.equals("trkpt", ignoreCase = true) ||
                    tagName.equals("rtept", ignoreCase = true) ||
                    tagName.equals("wpt", ignoreCase = true)
                ) {
                    val latStr = parser.getAttributeValue(null, "lat")
                    val lonStr = parser.getAttributeValue(null, "lon")
                    if (latStr != null && lonStr != null) {
                        try {
                            val lat = latStr.toDouble()
                            val lon = lonStr.toDouble()
                            points.add(GeoPoint(lat, lon))
                        } catch (_: NumberFormatException) {}
                    }
                }
            }
            eventType = parser.next()
        }
        return points
    }
}
