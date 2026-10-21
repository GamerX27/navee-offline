package com.xlabsx27.naveeoffline.protocol

/**
 * Everything the app knows about the connected scooter. Null means "not reported yet". Values are in
 * the scooter's native units: speeds km/h, distances km, voltage mV, current mA.
 */
data class Telemetry(
    // live (0x90 home report, 0x91/0x92 ride reports)
    val speedKmh: Double? = null,
    val soc: Int? = null,
    val rangeKm: Int? = null,
    val odometerKm: Double? = null,
    val tripKm: Double? = null,
    val tripMinutes: Int? = null,
    val tripMaxKmh: Double? = null,
    val tripAvgKmh: Double? = null,
    val gear: Int? = null,
    val faultRaw: Int? = null,
    val batteryStatus: Int? = null,
    val chargingState: Int? = null,
    val drivingStatus: Int? = null,
    val locked: Boolean? = null,

    // parameter block (0x70)
    val cruise: Int? = null,
    val tailLight: Int? = null,
    /** Unit on the scooter's own screen, see [DisplayUnit]. */
    val displayUnit: Int? = null,
    val autoLight: Int? = null,
    val ambientLight: Int? = null,
    val tcs: Int? = null,
    val startSpeed: Int? = null,
    val limitSpeed: Int? = null,
    val limitOn: Boolean? = null,
    val logoLight: Int? = null,
    val drl: Int? = null,
    val maxSpeed: Int? = null,
    val driveMode: Int? = null,
    val eco: Int? = null,

    // battery (0x72, refreshed by 0x90)
    val packMv: Long? = null,
    val packMa: Long? = null,
    val soh: Int? = null,
    val batteryTempC: Int? = null,
    val cycles: Int? = null,

    // identity (0x73, 0x74)
    val fwMeter: String? = null,
    val fwController: String? = null,
    val fwBms: String? = null,
    val fwScreen: String? = null,
    val fwUwb: String? = null,
    val serial: String? = null,
    val region: String? = null,
    val pid: String? = null,

    val updatedAt: Long = 0L,
) {
    val voltage: Double? get() = packMv?.let { it / 1000.0 }
    val current: Double? get() = packMa?.let { it / 1000.0 }
    val powerW: Double? get() = if (packMv != null && packMa != null) packMv * packMa / 1_000_000.0 else null
    val model: String? get() = NaveeModels.name(pid)
    val fault: Fault? get() = faultRaw?.let(NaveeFaults::decode)
}

object TelemetryDecoder {

    /** Returns [t] updated with whatever [f] carries. Unknown commands leave it unchanged. */
    fun apply(t: Telemetry, f: RxFrame, now: Long = System.currentTimeMillis()): Telemetry {
        val next = when (f.cmd) {
            Commands.READ_PARAMS -> if (f.ok) params(t, f) else t
            Commands.READ_BATTERY -> if (f.ok) battery(t, f) else t
            Commands.READ_FIRMWARE -> if (f.ok) firmware(t, f) else t
            Commands.READ_SERIAL -> if (f.ok) serial(t, f) else t
            Commands.REPORT_HOME -> home(t, f)
            Commands.REPORT_RIDE_V0 -> rideV0(t, f)
            Commands.REPORT_RIDE_V1 -> rideV1(t, f)
            else -> return t
        }
        return next.copy(updatedAt = now)
    }

    private fun params(t: Telemetry, f: RxFrame): Telemetry {
        val limit = f.u8(20)
        return t.copy(
            locked = f.u8(2)?.let { it != 0 } ?: t.locked,
            cruise = f.u8(3) ?: t.cruise,
            tailLight = f.u8(4) ?: t.tailLight,
            displayUnit = f.u8(7) ?: t.displayUnit,
            autoLight = f.u8(8) ?: t.autoLight,
            ambientLight = f.u8(10) ?: t.ambientLight,
            tcs = f.u8(11) ?: t.tcs,
            startSpeed = f.u8(19) ?: t.startSpeed,
            limitSpeed = limit?.and(0x7F) ?: t.limitSpeed,
            limitOn = limit?.let { it and 0x80 != 0 } ?: t.limitOn,
            logoLight = f.u8(23) ?: t.logoLight,
            drl = f.u8(24) ?: t.drl,
            maxSpeed = f.u8(25) ?: t.maxSpeed,
            // Verified on an E45 Pro: byte 1 follows every 0x58 write (3 Normal / 5 Turbo).
            driveMode = f.u8(1) ?: t.driveMode,
            eco = f.u8(32) ?: t.eco,
        )
    }

    private fun battery(t: Telemetry, f: RxFrame) = t.copy(
        soc = f.u8(1) ?: t.soc,
        packMv = f.le(2, 4) ?: t.packMv,
        packMa = f.le(6, 4)?.let(::signMagnitude) ?: t.packMa,
        soh = f.u8(10) ?: t.soh,
        batteryTempC = f.u8(11) ?: t.batteryTempC,
        cycles = f.le(13, 2)?.toInt() ?: t.cycles,
    )

    private fun firmware(t: Telemetry, f: RxFrame) = t.copy(
        fwMeter = f.ascii(0, 4) ?: t.fwMeter,
        fwController = f.ascii(4, 4) ?: t.fwController,
        fwBms = f.ascii(8, 4) ?: t.fwBms,
        fwScreen = f.ascii(12, 4) ?: t.fwScreen,
        fwUwb = f.ascii(16, 4) ?: t.fwUwb,
    )

    private val PID = Regex("[A-Za-z]?(\\d{4})")

    private fun serial(t: Telemetry, f: RxFrame): Telemetry {
        val sn = buildString {
            for (b in f.data) {
                val c = b.toInt() and 0xFF
                if (c in 0x20..0x7E) append(c.toChar())
            }
        }.trim()
        return t.copy(
            serial = sn,
            region = if (sn.length >= 10) sn.substring(8, 10) else t.region,
            pid = PID.find(sn)?.groupValues?.get(1) ?: t.pid,
        )
    }

    private fun home(t: Telemetry, f: RxFrame): Telemetry {
        val lockByte = f.u8(7)
        val mv = f.le(8, 4)?.takeIf { it > 0 }
        val ma = f.le(12, 4)?.takeIf { it != 0L }?.let(::signMagnitude)
        return t.copy(
            faultRaw = f.u8(0) ?: t.faultRaw,
            gear = f.u8(1) ?: t.gear,
            soc = f.u8(2) ?: t.soc,
            batteryStatus = f.u8(3) ?: t.batteryStatus,
            chargingState = f.u8(4) ?: t.chargingState,
            rangeKm = f.u8(6) ?: t.rangeKm,
            locked = lockByte?.takeIf { it > 0 }?.let { it - 1 != 0 } ?: t.locked,
            packMv = mv ?: t.packMv,
            packMa = ma ?: t.packMa,
        )
    }

    private fun rideV0(t: Telemetry, f: RxFrame) = t.copy(
        drivingStatus = f.u8(1) ?: t.drivingStatus,
        speedKmh = f.u8(2)?.toDouble() ?: t.speedKmh,
        rangeKm = f.u8(3) ?: t.rangeKm,
        tripKm = f.u8(4)?.toDouble() ?: t.tripKm,
        odometerKm = f.u8(8)?.toDouble() ?: t.odometerKm,
    )

    private fun rideV1(t: Telemetry, f: RxFrame): Telemetry {
        val odo32 = f.le(14, 4)?.takeIf { it > 0 }
        return t.copy(
            soc = f.u8(0) ?: t.soc,
            drivingStatus = f.u8(1) ?: t.drivingStatus,
            speedKmh = f.le(2, 2)?.let { it / 10.0 } ?: t.speedKmh,
            rangeKm = f.u8(4) ?: t.rangeKm,
            tripKm = f.le(5, 2)?.let { it / 10.0 } ?: t.tripKm,
            tripMinutes = f.u8(7) ?: t.tripMinutes,
            tripMaxKmh = f.le(8, 2)?.let { it / 10.0 } ?: t.tripMaxKmh,
            tripAvgKmh = f.le(10, 2)?.let { it / 10.0 } ?: t.tripAvgKmh,
            odometerKm = (odo32 ?: f.le(12, 2))?.let { it / 10.0 } ?: t.odometerKm,
        )
    }

    /** Current is sent as sign + magnitude: bit 31 set means negative. */
    private fun signMagnitude(v: Long): Long = if (v and 0x8000_0000L != 0L) -(v and 0x7FFF_FFFFL) else v
}
