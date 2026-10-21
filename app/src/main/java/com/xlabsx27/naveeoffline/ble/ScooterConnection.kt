package com.xlabsx27.naveeoffline.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.xlabsx27.naveeoffline.protocol.AuthSession
import com.xlabsx27.naveeoffline.protocol.Commands
import com.xlabsx27.naveeoffline.protocol.FrameAssembler
import com.xlabsx27.naveeoffline.protocol.NaveeFrame
import com.xlabsx27.naveeoffline.protocol.Redact
import com.xlabsx27.naveeoffline.protocol.RxFrame
import com.xlabsx27.naveeoffline.protocol.Setting
import com.xlabsx27.naveeoffline.protocol.Telemetry
import com.xlabsx27.naveeoffline.protocol.TelemetryDecoder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.ArrayDeque
import java.util.UUID

enum class LinkState { IDLE, CONNECTING, AUTHENTICATING, CONNECTED, RECONNECTING, NOT_A_SCOOTER }

data class LinkStatus(
    val state: LinkState = LinkState.IDLE,
    val address: String? = null,
    val name: String? = null,
    val rssi: Int? = null,
    /** True once the handshake armed the live report stream. */
    val armed: Boolean = false,
    /** The scooter refused this app's id: it is bound to another phone or account. */
    val rejected: Boolean = false,
) {
    val isConnected get() = state == LinkState.CONNECTED || state == LinkState.AUTHENTICATING
    /** The user wants a link to this scooter (connected or trying to get there). */
    val isWanted get() = state != LinkState.IDLE && state != LinkState.NOT_A_SCOOTER
}

/**
 * One GATT link to a NAVEE scooter. All GATT work runs on a dedicated thread; state is published
 * through [status] and [telemetry]. While a scooter is wanted, a dropped link is retried with backoff.
 */
@SuppressLint("MissingPermission")
class ScooterConnection(private val context: Context, private val clientId: ClientIdStore) {

    private val _status = MutableStateFlow(LinkStatus())
    val status: StateFlow<LinkStatus> = _status.asStateFlow()

    private val _telemetry = MutableStateFlow(Telemetry())
    val telemetry: StateFlow<Telemetry> = _telemetry.asStateFlow()

    private val _rssi = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    /** Every signal reading (about every 2 s while connected), including repeats of the same value. */
    val rssiReadings: SharedFlow<Int> = _rssi

    var autoReconnect = true

    /** Frame dumps only in debuggable builds; release builds also strip Log.v itself (proguard-rules.pro). */
    private val dumpFrames = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private val thread = HandlerThread("navee-ble").apply { start() }
    private val handler = Handler(thread.looper)
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    // Everything below is only touched on [thread].
    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private var target: String? = null
    private var targetName: String? = null
    private var mtuDone = false
    private var ready = false
    private val queue = ArrayDeque<ByteArray>()
    private var writing = false
    private val assembler = FrameAssembler()
    private var auth: AuthSession? = null
    private var reconnectDelay = RECONNECT_MIN_MS
    private var pollTick = 0
    /** Consecutive 0x90 polls the scooter refused (err 255 on an E45 Pro). */
    private var homeRefused = 0

    fun connect(address: String, name: String?) = handler.post {
        target = address
        targetName = name
        reconnectDelay = RECONNECT_MIN_MS
        handler.removeCallbacks(reconnectTask)
        openGatt(LinkState.CONNECTING)
    }

    fun disconnect() = handler.post {
        target = null
        handler.removeCallbacks(reconnectTask)
        val g = gatt
        gatt = null
        teardown()
        runCatching { g?.disconnect() }
        runCatching { g?.close() }
        _status.value = LinkStatus()
        _telemetry.value = Telemetry()
    }

    /** Writes a setting, then re-reads the parameter block so the UI shows what the scooter accepted. */
    fun write(setting: Setting, value: Int) = handler.post {
        if (!ready) return@post
        enqueue(setting.frame(value))
        handler.postDelayed({ if (ready) enqueue(NaveeFrame.read(Commands.READ_PARAMS)) }, REREAD_MS)
    }

    // ---- connection lifecycle -------------------------------------------------------------------

    private fun openGatt(state: LinkState) {
        val address = target ?: return
        teardown()
        runCatching { gatt?.close() }
        gatt = null
        val device: BluetoothDevice = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: run {
            Log.w(TAG, "no adapter or bad address")
            scheduleReconnect()
            return
        }
        val prev = _status.value
        _status.value = LinkStatus(state, address, targetName ?: prev.name, rejected = prev.rejected && prev.address == address)
        gatt = runCatching {
            // The Handler overload is deprecated on API 37 but its replacement does not exist below it.
            @Suppress("DEPRECATION")
            device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE, 0, handler)
        }.onFailure { Log.w(TAG, "connectGatt failed", it) }.getOrNull()
        if (gatt == null) scheduleReconnect() else handler.postDelayed(connectTimeout, CONNECT_TIMEOUT_MS)
    }

    private val connectTimeout = Runnable {
        if (!ready && target != null) {
            Log.i(TAG, "connect timeout")
            dropAndRetry()
        }
    }

    private val reconnectTask = Runnable {
        if (target != null) openGatt(LinkState.RECONNECTING)
    }

    private fun scheduleReconnect() {
        if (target == null || !autoReconnect) {
            if (target != null) {
                target = null
                _status.value = LinkStatus(name = _status.value.name)
            }
            return
        }
        _status.update { it.copy(state = LinkState.RECONNECTING, armed = false, rssi = null) }
        handler.removeCallbacks(reconnectTask)
        handler.postDelayed(reconnectTask, reconnectDelay)
        reconnectDelay = (reconnectDelay * 2).coerceAtMost(RECONNECT_MAX_MS)
    }

    private fun dropAndRetry() {
        val g = gatt
        teardown()
        runCatching { g?.disconnect() }
        runCatching { g?.close() }
        gatt = null
        _telemetry.value = Telemetry()
        scheduleReconnect()
    }

    private fun teardown() {
        handler.removeCallbacks(connectTimeout)
        handler.removeCallbacks(pollTask)
        handler.removeCallbacks(authTimeout)
        handler.removeCallbacks(mtuTimeout)
        handler.removeCallbacks(drainTask)
        handler.removeCallbacks(writeTimeout)
        writeChar = null
        notifyChar = null
        mtuDone = false
        ready = false
        writing = false
        queue.clear()
        assembler.reset()
        auth = null
        homeRefused = 0
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (g != gatt) return
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "connected, discovering services")
                handler.postDelayed({ if (g == gatt) g.discoverServices() }, DISCOVER_DELAY_MS)
            } else {
                Log.i(TAG, "disconnected (status $status)")
                dropAndRetry()
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (g != gatt) return
            val svc = g.getService(SERVICE)
            writeChar = svc?.getCharacteristic(WRITE)
            notifyChar = svc?.getCharacteristic(NOTIFY)
            if (writeChar == null || notifyChar == null) {
                Log.w(TAG, "NAVEE service not found")
                val name = _status.value.name
                target = null
                teardown()
                runCatching { g.disconnect() }
                runCatching { g.close() }
                gatt = null
                _status.value = LinkStatus(LinkState.NOT_A_SCOOTER, name = name)
                return
            }
            // The stock app negotiates MTU 148 before opening notifications.
            if (g.requestMtu(MTU)) handler.postDelayed(mtuTimeout, MTU_TIMEOUT_MS) else afterMtu()
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (g == gatt && notifyChar != null) afterMtu()
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            if (g != gatt || d.uuid != CCCD) return
            if (status == BluetoothGatt.GATT_SUCCESS) onNotifyReady() else dropAndRetry()
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            if (g == gatt) onBytes(value)
        }

        @Deprecated("Pre-Android 13 variant")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && g == gatt) {
                @Suppress("DEPRECATION")
                c.value?.let(::onBytes)
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (g != gatt) return
            handler.removeCallbacks(writeTimeout)
            writing = false
            handler.postDelayed(drainTask, WRITE_GAP_MS)
        }

        override fun onReadRemoteRssi(g: BluetoothGatt, rssi: Int, status: Int) {
            if (g == gatt && status == BluetoothGatt.GATT_SUCCESS) {
                _status.update { it.copy(rssi = rssi) }
                _rssi.tryEmit(rssi)
            }
        }
    }

    private val mtuTimeout = Runnable { afterMtu() }

    private fun afterMtu() {
        if (mtuDone) return
        mtuDone = true
        handler.removeCallbacks(mtuTimeout)
        handler.postDelayed({ enableNotifications() }, MTU_SETTLE_MS)
    }

    @Suppress("DEPRECATION")
    private fun enableNotifications() {
        val g = gatt ?: return
        val c = notifyChar ?: return
        g.setCharacteristicNotification(c, true)
        val d = c.getDescriptor(CCCD) ?: return onNotifyReady()
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
        } else {
            d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            g.writeDescriptor(d)
        }
        if (!started) dropAndRetry()
    }

    private fun onNotifyReady() {
        if (ready) return
        ready = true
        handler.removeCallbacks(connectTimeout)
        reconnectDelay = RECONNECT_MIN_MS
        _status.update { it.copy(state = LinkState.AUTHENTICATING, address = target) }
        val session = AuthSession(clientId.id)
        auth = session
        enqueue(session.start())
        handler.postDelayed(authTimeout, AUTH_TIMEOUT_MS)
    }

    private val authTimeout = Runnable {
        auth?.takeUnless { it.finished }?.let { onAuthFinished(it.timeout()) }
    }

    private fun onAuthFinished(result: AuthSession.Step.Finished) {
        handler.removeCallbacks(authTimeout)
        Log.i(TAG, "handshake: ${result.reason}")
        _status.update { it.copy(state = LinkState.CONNECTED, armed = result.armed, rejected = result.rejected) }
        enqueue(Commands.clockSync())
        enqueue(NaveeFrame.read(Commands.READ_FAULT_HISTORY))
        handler.postDelayed({
            if (!ready) return@postDelayed
            enqueue(NaveeFrame.read(Commands.READ_SERIAL))
            enqueue(NaveeFrame.read(Commands.READ_PARAMS))
            enqueue(NaveeFrame.read(Commands.READ_BATTERY))
            enqueue(NaveeFrame.read(Commands.READ_FIRMWARE))
        }, STATUS_READ_DELAY_MS)
        pollTick = 0
        handler.postDelayed(pollTask, POLL_MS)
    }

    // ---- incoming -------------------------------------------------------------------------------

    private fun onBytes(bytes: ByteArray) {
        for (f in assembler.feed(bytes)) {
            if (dumpFrames) Log.v(TAG, "RX ${Redact.rx(f)}")
            handleAuth(f)
            if (f.cmd == Commands.REPORT_HOME) homeRefused = if (f.ok) 0 else homeRefused + 1
            _telemetry.update { TelemetryDecoder.apply(it, f) }
        }
    }

    private fun handleAuth(f: RxFrame) {
        val session = auth ?: return
        when (val step = session.onFrame(f)) {
            is AuthSession.Step.Send -> enqueue(step.frame)
            is AuthSession.Step.Finished -> onAuthFinished(step)
            null -> Unit
        }
    }

    // ---- polling --------------------------------------------------------------------------------

    private val pollTask = object : Runnable {
        override fun run() {
            if (!ready) return
            pollTick++
            // Skip a round rather than let a slow link build up a backlog.
            if (queue.size < 4) {
                // Models that refuse the 0x90 pull still get an occasional retry, in case it starts
                // answering (e.g. while riding), but not every 0.5 s.
                if (homeRefused < HOME_REFUSED_LIMIT || pollTick % HOME_BACKOFF_TICKS == 0) {
                    enqueue(NaveeFrame.read(Commands.REPORT_HOME))
                }
                if (pollTick % 4 == 0) enqueue(NaveeFrame.read(Commands.READ_PARAMS))
                if (pollTick % 10 == 0) enqueue(NaveeFrame.read(Commands.READ_BATTERY))
            }
            if (pollTick % 4 == 0 && !writing) runCatching { gatt?.readRemoteRssi() }
            handler.postDelayed(this, POLL_MS)
        }
    }

    // ---- outgoing -------------------------------------------------------------------------------

    private fun enqueue(frame: ByteArray) {
        queue.addLast(frame)
        drain()
    }

    private val drainTask = Runnable { drain() }

    private val writeTimeout = Runnable {
        // Some stacks drop the write callback; do not let the queue stall forever.
        writing = false
        drain()
    }

    @Suppress("DEPRECATION")
    private fun drain() {
        if (writing) return
        val g = gatt ?: return
        val c = writeChar ?: return
        val frame = queue.pollFirst() ?: return
        val type = if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(c, frame, type) == BluetoothStatusCodes.SUCCESS
        } else {
            c.writeType = type
            c.value = frame
            g.writeCharacteristic(c)
        }
        if (!ok) {
            // GATT busy (e.g. an RSSI read in flight): put it back and retry shortly.
            queue.addFirst(frame)
            handler.postDelayed(drainTask, WRITE_GAP_MS)
            return
        }
        if (dumpFrames) Log.v(TAG, "TX ${Redact.tx(frame)}")
        writing = true
        handler.postDelayed(writeTimeout, WRITE_TIMEOUT_MS)
    }

    companion object {
        private const val TAG = "ScooterConnection"

        val SERVICE: UUID = UUID.fromString("0000d0ff-3c17-d293-8e48-14fe2e4da212")
        val WRITE: UUID = UUID.fromString("0000b002-0000-1000-8000-00805f9b34fb")
        val NOTIFY: UUID = UUID.fromString("0000b003-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val MTU = 148
        private const val DISCOVER_DELAY_MS = 300L
        private const val MTU_TIMEOUT_MS = 1_500L
        private const val MTU_SETTLE_MS = 100L
        private const val CONNECT_TIMEOUT_MS = 20_000L
        private const val AUTH_TIMEOUT_MS = 3_000L
        private const val STATUS_READ_DELAY_MS = 500L
        private const val WRITE_GAP_MS = 200L
        private const val WRITE_TIMEOUT_MS = 1_000L
        private const val POLL_MS = 500L
        private const val HOME_REFUSED_LIMIT = 3
        private const val HOME_BACKOFF_TICKS = 20
        private const val REREAD_MS = 350L
        private const val RECONNECT_MIN_MS = 1_000L
        private const val RECONNECT_MAX_MS = 30_000L
    }
}
