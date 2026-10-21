package com.xlabsx27.naveeoffline.ui

import android.content.res.Resources
import com.xlabsx27.naveeoffline.R
import com.xlabsx27.naveeoffline.data.Units
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object Format {
    private const val KM_PER_MILE = 1.609344

    fun speedValue(kmh: Double, units: Units) = if (units == Units.IMPERIAL) kmh / KM_PER_MILE else kmh
    fun speedUnit(units: Units) = if (units == Units.IMPERIAL) "mph" else "km/h"
    fun speed(kmh: Double?, units: Units, decimals: Int = 1): String =
        kmh?.let { "${num(speedValue(it, units), decimals)} ${speedUnit(units)}" } ?: "–"

    fun distanceUnit(units: Units) = if (units == Units.IMPERIAL) "mi" else "km"

    /** Short distances in m/ft, longer ones in km/mi. */
    fun distance(meters: Double?, units: Units): String {
        if (meters == null) return "–"
        return if (units == Units.IMPERIAL) {
            val ft = meters * 3.28084
            if (ft < 1000) "${num(ft, 0)} ft" else "${num(meters / 1000 / KM_PER_MILE, 2)} mi"
        } else {
            if (meters < 1000) "${num(meters, 0)} m" else "${num(meters / 1000, 2)} km"
        }
    }

    fun km(km: Number?, units: Units, decimals: Int = 1): String =
        km?.let { "${num(if (units == Units.IMPERIAL) it.toDouble() / KM_PER_MILE else it.toDouble(), decimals)} ${distanceUnit(units)}" }
            ?: "–"

    fun whPerDistance(whPerKm: Double?, units: Units): String =
        whPerKm?.let { "${num(if (units == Units.IMPERIAL) it * KM_PER_MILE else it, 1)} Wh/${distanceUnit(units)}" } ?: "–"

    fun duration(res: Resources, ms: Long): String {
        val s = ms / 1000
        val h = (s / 3600).toInt()
        val m = ((s % 3600) / 60).toInt()
        return when {
            h > 0 -> res.getString(R.string.duration_h_min, h, m)
            m > 0 -> res.getString(R.string.duration_min, m)
            else -> res.getString(R.string.duration_s, (s % 60).toInt())
        }
    }

    fun ago(res: Resources, time: Long, now: Long = System.currentTimeMillis()): String {
        val min = ((now - time) / 60_000).toInt()
        return when {
            min < 1 -> res.getString(R.string.ago_now)
            min < 60 -> res.getString(R.string.ago_min, min)
            min < 48 * 60 -> res.getString(R.string.ago_h, min / 60)
            else -> res.getQuantityString(R.plurals.ago_days, min / 1440, min / 1440)
        }
    }

    fun dateTime(time: Long): String = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()))

    fun date(time: Long): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
        .format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()))

    /** Short weekday and date, e.g. "Tue 21 Oct" / "tir. 21. okt.". */
    fun day(res: Resources, date: java.time.LocalDate): String {
        val locale = res.configuration.locales[0] ?: Locale.getDefault()
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMM")
        return DateTimeFormatter.ofPattern(pattern, locale).format(date)
    }

    fun time(time: Long): String = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()))

    fun num(v: Double, decimals: Int): String = String.format(Locale.getDefault(), "%.${decimals}f", v)
}
