package com.xlabsx27.naveeoffline.export

import com.xlabsx27.naveeoffline.data.TrackPoint
import com.xlabsx27.naveeoffline.data.Trip
import java.io.OutputStream
import java.time.Instant
import java.util.Locale

/** Writes a trip as GPX 1.1. Scooter speed and battery go into a namespaced extension block. */
object GpxWriter {
    fun write(trip: Trip, points: List<TrackPoint>, out: OutputStream) {
        val w = out.bufferedWriter()
        w.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        w.appendLine(
            """<gpx version="1.1" creator="Navee Offline" xmlns="http://www.topografix.com/GPX/1/1" """ +
                """xmlns:nv="https://git.xlabsx27.com/X27/navee-offline/gpx/1">""",
        )
        w.appendLine("  <metadata><time>${Instant.ofEpochMilli(trip.startedAt)}</time></metadata>")
        w.appendLine("  <trk>")
        w.appendLine("    <name>Trip ${Instant.ofEpochMilli(trip.startedAt)}</name>")
        w.appendLine("    <trkseg>")
        for (p in points) {
            w.append("      <trkpt lat=\"").append(fmt(p.lat, 7)).append("\" lon=\"").append(fmt(p.lon, 7)).append("\">")
            p.altitude?.let { w.append("<ele>").append(fmt(it, 1)).append("</ele>") }
            w.append("<time>").append(Instant.ofEpochMilli(p.time).toString()).append("</time>")
            val ext = buildString {
                p.scooterSpeedKmh?.let { append("<nv:speed>").append(fmt(it.toDouble(), 1)).append("</nv:speed>") }
                p.soc?.let { append("<nv:soc>").append(it).append("</nv:soc>") }
                p.voltage?.let { append("<nv:voltage>").append(fmt(it.toDouble(), 2)).append("</nv:voltage>") }
                p.currentA?.let { append("<nv:current>").append(fmt(it.toDouble(), 2)).append("</nv:current>") }
            }
            if (ext.isNotEmpty()) w.append("<extensions>").append(ext).append("</extensions>")
            w.appendLine("</trkpt>")
        }
        w.appendLine("    </trkseg>")
        w.appendLine("  </trk>")
        w.appendLine("</gpx>")
        w.flush()
    }

    private fun fmt(v: Double, decimals: Int) = String.format(Locale.ROOT, "%.${decimals}f", v)
}
