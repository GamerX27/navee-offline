package com.xlabsx27.naveeoffline.protocol

import java.util.TimeZone

object Commands {
    const val READ_PARAMS = 0x70
    const val READ_BATTERY = 0x72
    const val READ_FIRMWARE = 0x73
    const val READ_SERIAL = 0x74
    const val READ_FAULT_HISTORY = 0x7B
    const val REPORT_HOME = 0x90
    const val REPORT_RIDE_V0 = 0x91
    const val REPORT_RIDE_V1 = 0x92

    private const val CMD_SYSTEM = 0x6F
    private const val SUB_CLOCK = 0x06

    /** Clock sync: 0x6F sub 6 + local wall-clock seconds since the epoch, big-endian. */
    fun clockSync(nowMs: Long = System.currentTimeMillis(), tz: TimeZone = TimeZone.getDefault()): ByteArray {
        val local = nowMs / 1000 + tz.getOffset(nowMs) / 1000
        return NaveeFrame.write(
            CMD_SYSTEM,
            byteArrayOf(
                SUB_CLOCK.toByte(),
                (local ushr 24).toByte(), (local ushr 16).toByte(), (local ushr 8).toByte(), local.toByte(),
            ),
        )
    }
}

/** A writable scooter setting. Most take one value byte; a few are sub-commands of a shared opcode. */
enum class Setting(val cmd: Int, val sub: Int? = null) {
    LOCK(0x51),
    CRUISE(0x52),
    TAIL_LIGHT(0x54),
    DISPLAY_UNIT(0x55),
    AUTO_LIGHT(0x57),
    DRIVE_MODE(0x58),
    AMBIENT_LIGHT(0x5E),
    TCS(0x5F),
    ZERO_START(0x6A),
    LOGO_LIGHT(0x6D, 2),
    DRL(0x6D, 3),
    ECO(0x6F, 5);

    fun frame(value: Int): ByteArray {
        val v = value.toByte()
        return NaveeFrame.write(cmd, if (sub == null) byteArrayOf(v) else byteArrayOf(sub.toByte(), v))
    }
}

/** Values for [Setting.DISPLAY_UNIT]. Verified on an E45 Pro: 1 shows km/h, 0 shows mph. */
object DisplayUnit {
    const val MPH = 0
    const val KMH = 1
}

object DriveMode {
    const val NORMAL = 3
    const val TURBO = 5
}
