package com.rapidresq.app.ui.components

import android.R
import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.rapidresq.app.ui.theme.BrandNavy
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

enum class MapPinType {
    INCIDENT_CRITICAL,
    INCIDENT_NORMAL,
    RESPONDER_UNIT,
    SELECTED_LOCATION
}

data class MapPin(
    val id: String,
    val title: String,
    val snippet: String,
    val lat: Double,
    val lng: Double,
    val pinType: MapPinType
)

@Composable
fun RapidResQMapCard(
    pins: List<MapPin>,
    centerLat: Double = 22.7196,
    centerLng: Double = 75.8577,
    zoom: Double = 13.5,
    height: Dp = 230.dp,
    onMapTap: ((Double, Double) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Initialize osmdroid user agent configuration before creating view
    remember {
        Configuration.getInstance().userAgentValue = context.packageName
        true
    }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewRef?.onPause()
                Lifecycle.Event.ON_DESTROY -> mapViewRef?.onDetach()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewRef?.onDetach()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp)),
        color = BrandNavy
    ) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(zoom)
                    controller.setCenter(GeoPoint(centerLat, centerLng))

                    // Allow smooth panning inside scrollable Compose parents
                    setOnTouchListener { v, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        false
                    }

                    mapViewRef = this
                }
            },
            update = { mapView ->
                mapViewRef = mapView
                mapView.overlays.clear()

                // Tap listener for coordinate selection
                if (onMapTap != null) {
                    val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            if (p != null) {
                                onMapTap(p.latitude, p.longitude)
                            }
                            return true
                        }

                        override fun longPressHelper(p: GeoPoint?): Boolean = false
                    })
                    mapView.overlays.add(eventsOverlay)
                }

                // Render pins
                pins.forEach { pin ->
                    val marker = Marker(mapView).apply {
                        id = pin.id
                        position = GeoPoint(pin.lat, pin.lng)
                        title = pin.title
                        snippet = pin.snippet
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                        val pinDrawable = getTintedMarkerDrawable(ctx = context, pinType = pin.pinType)
                        if (pinDrawable != null) {
                            icon = pinDrawable
                        }
                    }
                    mapView.overlays.add(marker)
                }

                if (pins.isNotEmpty() && (centerLat == 22.7196 && centerLng == 75.8577)) {
                    val firstPin = pins.first()
                    mapView.controller.setCenter(GeoPoint(firstPin.lat, firstPin.lng))
                } else {
                    mapView.controller.setCenter(GeoPoint(centerLat, centerLng))
                }

                mapView.invalidate()
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun getTintedMarkerDrawable(ctx: Context, pinType: MapPinType): Drawable? {
    val drawable = ContextCompat.getDrawable(ctx, R.drawable.ic_menu_myplaces)?.mutate() ?: return null

    val colorInt = when (pinType) {
        MapPinType.INCIDENT_CRITICAL -> AndroidColor.parseColor("#DC2626") // Emergency Red
        MapPinType.INCIDENT_NORMAL -> AndroidColor.parseColor("#D97706")   // Amber
        MapPinType.RESPONDER_UNIT -> AndroidColor.parseColor("#1D4ED8")     // Brand Blue
        MapPinType.SELECTED_LOCATION -> AndroidColor.parseColor("#059669")  // Emerald Green
    }

    drawable.setColorFilter(colorInt, PorterDuff.Mode.SRC_IN)
    return drawable
}
