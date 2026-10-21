package com.xlabsx27.naveeoffline.map

import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.xlabsx27.naveeoffline.R
import com.xlabsx27.naveeoffline.service.LatLon
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.Dimension
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.MapPosition
import org.mapsforge.core.util.LatLongUtils
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.datastore.MultiMapDataStore
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.overlay.Circle
import org.mapsforge.map.layer.overlay.FixedPixelCircle
import org.mapsforge.map.layer.overlay.Marker
import org.mapsforge.map.layer.overlay.Polyline
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes
import org.mapsforge.map.scalebar.DefaultMapScaleBar
import org.mapsforge.map.scalebar.ImperialUnitAdapter
import org.mapsforge.map.scalebar.MetricUnitAdapter
import org.mapsforge.map.view.InputListener
import java.io.File

data class MyPosition(val lat: Double, val lon: Double, val accuracyM: Float?)

/** One track on the map. [emphasis] draws it wider, e.g. the trip selected in a list. */
data class MapPath(val points: List<LatLon>, val color: Int, val emphasis: Boolean = false)

/**
 * Offline OpenStreetMap view rendered from local Mapsforge files. Without any map file it still draws
 * the track and markers on a plain background, so recording works before a map is imported.
 */
@Composable
fun OfflineMap(
    mapFiles: List<File>,
    modifier: Modifier = Modifier,
    paths: List<MapPath> = emptyList(),
    me: MyPosition? = null,
    parked: LatLon? = null,
    follow: Boolean = false,
    /** Zooms to fit [fitPoints] (or all of [paths]) whenever this key changes; null never fits. */
    fitKey: Any? = null,
    fitPoints: List<LatLon>? = null,
    dark: Boolean = false,
    cacheId: String = "map",
    metric: Boolean = true,
    onUserPan: () -> Unit = {},
) {
    val context = LocalContext.current
    val holder = remember(cacheId) { MapHolder(context, cacheId) }
    DisposableEffect(holder) { onDispose { holder.destroy() } }
    holder.onUserPan = onUserPan
    AndroidView(
        factory = { holder.view },
        modifier = modifier,
        update = {
            holder.setFiles(mapFiles)
            holder.setMetric(metric)
            holder.setDark(dark)
            holder.setPaths(paths)
            holder.setMe(me, follow)
            holder.setParked(parked)
            if (fitKey != null) holder.fit(fitKey, fitPoints ?: paths.flatMap { it.points })
        },
    )
}

private class MapHolder(context: Context, cacheId: String) {
    val view = MapView(context)
    private val factory = AndroidGraphicFactory.INSTANCE
    private val tileCache: TileCache
    private var files: List<File>? = null
    private var tileLayer: TileRendererLayer? = null
    private var store: MultiMapDataStore? = null
    private var dark: Boolean? = null
    private val tracks = mutableListOf<Track>()
    private var fittedKey: Any? = null
    private var hasPosition = false
    private val density = view.resources.displayMetrics.density
    var onUserPan: () -> Unit = {}

    /** One drawn path and the points it currently shows, so a growing live track only appends. */
    private inner class Track {
        val paint = factory.createPaint().apply { setStyle(Style.STROKE) }
        val line = Polyline(paint, factory)
        var shown: List<LatLon> = emptyList()
    }

    private val meFill = factory.createPaint().apply { color = 0xFF1A73E8.toInt(); setStyle(Style.FILL) }
    private val meStroke = factory.createPaint().apply {
        color = 0xFFFFFFFF.toInt(); setStyle(Style.STROKE); setStrokeWidth(3 * view.resources.displayMetrics.density)
    }
    private val accFill = factory.createPaint().apply { color = 0x221A73E8; setStyle(Style.FILL) }
    // FixedPixelCircle scales the radius by the display density itself, so this is in dp.
    private val meDot = FixedPixelCircle(null, 8f, meFill, meStroke)
    private val meAccuracy = Circle(null, 0f, accFill, null)

    private val parkedMarker = run {
        val d = requireNotNull(ContextCompat.getDrawable(context, R.drawable.ic_map_parked))
        val bmp = AndroidGraphicFactory.convertToBitmap(d)
        Marker(null, bmp, 0, -bmp.height / 2)
    }

    init {
        view.isClickable = true
        view.mapScaleBar.isVisible = true
        view.setBuiltInZoomControls(false)
        // Very low zooms over a country-sized map are slow and memory hungry; region level is enough.
        view.setZoomLevelMin(6)
        view.setZoomLevelMax(20)
        tileCache = AndroidUtil.createTileCache(
            context, cacheId, view.model.displayModel.tileSize, 1f, view.model.frameBufferModel.overdrawFactor,
        )
        view.addInputListener(object : InputListener {
            override fun onMoveEvent() = onUserPan()
            override fun onZoomEvent() = Unit
        })
        view.model.mapViewPosition.mapPosition = MapPosition(LatLong(50.0, 10.0), 6)
    }

    fun setFiles(newFiles: List<File>) {
        if (newFiles == files) return
        files = newFiles
        val layers = view.layerManager.layers
        tileLayer?.let { layers.remove(it); it.onDestroy() }
        store?.close()
        tileLayer = null
        store = null
        tileCache.purge()
        if (newFiles.isEmpty()) return

        val multi = MultiMapDataStore(MultiMapDataStore.DataPolicy.RETURN_ALL)
        newFiles.forEach { f -> runCatching { multi.addMapDataStore(MapFile(f), false, false) } }
        val layer = TileRendererLayer(tileCache, multi, view.model.mapViewPosition, factory)
        layer.setXmlRenderTheme(MapsforgeThemes.DEFAULT)
        layers.add(0, layer)
        tileLayer = layer
        store = multi
        if (!hasPosition) {
            // Street level at the map's own start point, not the whole country at once.
            val start = multi.startPosition() ?: multi.boundingBox().centerPoint
            view.model.mapViewPosition.mapPosition = MapPosition(start, 14)
        }
    }

    fun setMetric(metric: Boolean) {
        val bar = view.mapScaleBar as? DefaultMapScaleBar ?: return
        val unit = if (metric) MetricUnitAdapter.INSTANCE else ImperialUnitAdapter.INSTANCE
        if (bar.distanceUnitAdapter === unit && bar.scaleBarMode == DefaultMapScaleBar.ScaleBarMode.SINGLE) return
        bar.scaleBarMode = DefaultMapScaleBar.ScaleBarMode.SINGLE
        bar.distanceUnitAdapter = unit
    }

    fun setDark(on: Boolean) {
        if (on == dark) return
        dark = on
        if (on) {
            // Invert luminance but keep hue (invert + 180° hue turn): c' = c - 2·luma + 255.
            val r = 2 * 0.213f
            val g = 2 * 0.715f
            val b = 2 * 0.072f
            val m = ColorMatrix(floatArrayOf(
                1 - r, -g, -b, 0f, 255f,
                -r, 1 - g, -b, 0f, 255f,
                -r, -g, 1 - b, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            ))
            view.setLayerType(View.LAYER_TYPE_HARDWARE, android.graphics.Paint().apply { colorFilter = ColorMatrixColorFilter(m) })
        } else {
            view.setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }

    fun setPaths(paths: List<MapPath>) {
        val layers = view.layerManager.layers
        while (tracks.size > paths.size) {
            val t = tracks.removeAt(tracks.size - 1)
            layers.remove(t.line)
            t.line.onDestroy()
        }
        while (tracks.size < paths.size) {
            val t = Track()
            // Tracks sit right above the map tiles, under the position dot and the parking pin.
            layers.add(if (tileLayer != null) 1 + tracks.size else tracks.size, t.line)
            tracks += t
        }
        paths.forEachIndexed { i, p ->
            val t = tracks[i]
            t.paint.color = p.color
            t.paint.strokeWidth = (if (p.emphasis) 8 else 5) * density
            val path = p.points
            if (path !== t.shown) {
                val shown = t.shown
                val prefix = shown.isNotEmpty() && path.size >= shown.size && path[shown.size - 1] == shown.last()
                if (prefix) {
                    t.line.addPoints(path.subList(shown.size, path.size).map { LatLong(it.lat, it.lon) })
                } else {
                    t.line.clear()
                    t.line.addPoints(path.map { LatLong(it.lat, it.lon) })
                }
                t.shown = path
            }
            t.line.requestRedraw()
        }
    }

    fun setMe(me: MyPosition?, follow: Boolean) {
        val layers = view.layerManager.layers
        if (me == null) {
            layers.remove(meAccuracy)
            layers.remove(meDot)
            return
        }
        val ll = LatLong(me.lat, me.lon)
        meDot.setLatLong(ll)
        meAccuracy.setLatLong(ll)
        meAccuracy.radius = me.accuracyM ?: 0f
        if (!layers.contains(meAccuracy)) layers.add(meAccuracy)
        if (!layers.contains(meDot)) layers.add(meDot)
        meDot.requestRedraw()
        if (follow) {
            if (!hasPosition || view.model.mapViewPosition.zoomLevel < 14) {
                view.model.mapViewPosition.mapPosition = MapPosition(ll, 17)
            } else {
                view.model.mapViewPosition.animateTo(ll)
            }
            hasPosition = true
        }
    }

    fun setParked(p: LatLon?) {
        val layers = view.layerManager.layers
        if (p == null) {
            layers.remove(parkedMarker)
            return
        }
        parkedMarker.latLong = LatLong(p.lat, p.lon)
        if (!layers.contains(parkedMarker)) layers.add(parkedMarker)
        parkedMarker.requestRedraw()
    }

    /** Zooms to [points] once per [key], as soon as the view has a size. */
    fun fit(key: Any, points: List<LatLon>) {
        if (key == fittedKey || points.isEmpty()) return
        view.post {
            if (key == fittedKey || view.width == 0) return@post
            fittedKey = key
            hasPosition = true
            val box = BoundingBox(
                points.minOf { it.lat }, points.minOf { it.lon }, points.maxOf { it.lat }, points.maxOf { it.lon },
            )
            val zoom = LatLongUtils.zoomForBounds(
                Dimension(view.width, view.height), box, view.model.displayModel.tileSize,
            ).toInt().coerceIn(3, 18) - 1
            view.model.mapViewPosition.mapPosition = MapPosition(box.centerPoint, zoom.toByte())
        }
    }

    fun destroy() {
        // destroyAll() also destroys every layer and the tile cache of each tile layer.
        runCatching { view.destroyAll() }
        runCatching { store?.close() }
        runCatching { tileCache.destroy() }
    }
}
