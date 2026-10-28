package com.blacksingularities.openmesh.mesh

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Stan warstwy radiowej. */
sealed interface LinkState {
    data object Off : LinkState
    data object Starting : LinkState
    data object On : LinkState
    data class Error(val message: String) : LinkState
}

/** Połączone urządzenie sąsiednie. */
data class Link(val endpoint: String, val peerId: String, val name: String, val since: Long)

/**
 * Transport przez Nearby Connections: jednocześnie ogłasza się i szuka sąsiadów, automatycznie
 * łączy się z każdym wykrytym węzłem OpenMesh (Bluetooth, BLE, Wi‑Fi Direct / hotspot – medium
 * dobiera system). Uwierzytelnianie odbywa się wyżej, przez podpisy pakietów.
 */
class NearbyTransport(private val ctx: Context, private val listener: Listener) {
    interface Listener {
        fun onLinkUp(link: Link)
        fun onLinkDown(endpoint: String)
        fun onBytes(endpoint: String, bytes: ByteArray)
    }

    private val client by lazy { Nearby.getConnectionsClient(ctx) }
    private val main = Handler(Looper.getMainLooper())

    private val _links = MutableStateFlow<Map<String, Link>>(emptyMap())
    val links: StateFlow<Map<String, Link>> = _links
    private val _state = MutableStateFlow<LinkState>(LinkState.Off)
    val state: StateFlow<LinkState> = _state
    private val _nearby = MutableStateFlow(0)

    /** Liczba wykrytych w pobliżu urządzeń (także tych, z którymi trwa łączenie). */
    val nearby: StateFlow<Int> = _nearby

    private val pending = mutableSetOf<String>()
    private val found = mutableMapOf<String, String>()
    private val names = mutableMapOf<String, String>()
    private var myId = ""
    private var myName = ""
    private var strategy = Strategy.P2P_CLUSTER
    private var running = false
    private var currentTopology = Topology.CLUSTER

    fun playServicesAvailable(): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ctx) == ConnectionResult.SUCCESS

    /** Wszystkie operacje na stanie transportu wykonują się w wątku głównym (tam przychodzą wywołania Nearby). */
    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    fun start(id: String, name: String, topology: Topology) = onMain { startNow(id, name, topology) }

    fun stop() = onMain { stopNow() }

    private fun startNow(id: String, name: String, topology: Topology) {
        if (running && id == myId && name == myName && topology == currentTopology) return
        if (running) stopNow()
        currentTopology = topology
        if (!playServicesAvailable()) {
            _state.value = LinkState.Error("Brak Usług Google Play – Nearby Connections jest niedostępne na tym urządzeniu")
            return
        }
        myId = id
        myName = name
        strategy = if (topology == Topology.STAR) Strategy.P2P_STAR else Strategy.P2P_CLUSTER
        running = true
        _state.value = LinkState.Starting
        advertise()
        discover()
    }

    private fun stopNow() {
        running = false
        main.removeCallbacksAndMessages(null)
        runCatching { client.stopAdvertising() }
        runCatching { client.stopDiscovery() }
        runCatching { client.stopAllEndpoints() }
        pending.clear()
        found.clear()
        names.clear()
        _links.value = emptyMap()
        _nearby.value = 0
        _state.value = LinkState.Off
    }

    /** Wysyła do wszystkich sąsiadów poza [except]; zwraca liczbę adresatów. */
    fun broadcast(bytes: ByteArray, except: String? = null): Int {
        val targets = _links.value.keys.filter { it != except }
        if (targets.isEmpty()) return 0
        client.sendPayload(targets, Payload.fromBytes(bytes))
        return targets.size
    }

    fun send(endpoint: String, bytes: ByteArray) {
        if (_links.value.containsKey(endpoint)) client.sendPayload(endpoint, Payload.fromBytes(bytes))
    }

    fun disconnect(endpoint: String) = onMain {
        runCatching { client.disconnectFromEndpoint(endpoint) }
        drop(endpoint)
    }

    private fun endpointName() = (myId + "|" + myName).take(120)

    private fun parse(endpointName: String): Pair<String, String> {
        val i = endpointName.indexOf('|')
        return if (i < 0) endpointName to endpointName else endpointName.substring(0, i) to endpointName.substring(i + 1)
    }

    private fun advertise() {
        val opts = AdvertisingOptions.Builder().setStrategy(strategy).build()
        client.startAdvertising(endpointName(), SERVICE_ID, lifecycle, opts)
            .addOnSuccessListener { if (running) _state.value = LinkState.On }
            .addOnFailureListener { fail(it) }
    }

    private fun discover() {
        val opts = DiscoveryOptions.Builder().setStrategy(strategy).build()
        client.startDiscovery(SERVICE_ID, discovery, opts)
            .addOnSuccessListener { if (running) _state.value = LinkState.On }
            .addOnFailureListener { fail(it) }
    }

    /** Odświeżenie wykrywania po rozłączeniu – Nearby nie zawsze zgłasza ponownie ten sam węzeł. */
    private fun rediscover() {
        if (!running) return
        runCatching { client.stopDiscovery() }
        main.postDelayed({ if (running) discover() }, 1_500)
    }

    private fun fail(e: Exception) {
        val code = (e as? ApiException)?.statusCode
        if (code == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING || code == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING) return
        _state.value = LinkState.Error(
            when (code) {
                ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH,
                ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_ADMIN,
                ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_COARSE_LOCATION,
                ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_WIFI_STATE,
                ConnectionsStatusCodes.MISSING_PERMISSION_CHANGE_WIFI_STATE,
                -> "Brak uprawnień do Bluetooth / Wi‑Fi – nadaj je w ustawieniach"
                ConnectionsStatusCodes.STATUS_BLUETOOTH_ERROR -> "Błąd Bluetooth – włącz Bluetooth i spróbuj ponownie"
                ConnectionsStatusCodes.STATUS_RADIO_ERROR -> "Radio wyłączone – włącz Bluetooth i Wi‑Fi (działa też w trybie samolotowym)"
                else -> "Nie udało się uruchomić sieci (" + (code ?: e.message) + ")"
            },
        )
        // Ponowna próba – np. po włączeniu Bluetooth przez użytkownika.
        main.postDelayed({
            if (running && _state.value is LinkState.Error) {
                _state.value = LinkState.Starting
                advertise()
                discover()
            }
        }, 15_000)
    }

    private fun request(endpoint: String) {
        if (!running || endpoint in pending || _links.value.containsKey(endpoint)) return
        pending += endpoint
        client.requestConnection(endpointName(), endpoint, lifecycle)
            .addOnFailureListener {
                pending -= endpoint
                val code = (it as? ApiException)?.statusCode
                if (code != ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT) {
                    main.postDelayed({ if (found.containsKey(endpoint)) request(endpoint) }, 5_000L + (0..4000).random())
                }
            }
    }

    private fun linkedTo(peerId: String) = _links.value.values.any { it.peerId == peerId }

    private fun drop(endpoint: String) {
        pending -= endpoint
        names -= endpoint
        if (_links.value.containsKey(endpoint)) {
            _links.update { it - endpoint }
            listener.onLinkDown(endpoint)
        }
    }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId != SERVICE_ID) return
            val (peerId, _) = parse(info.endpointName)
            if (peerId == myId) return
            found[endpointId] = peerId
            _nearby.value = found.size
            if (linkedTo(peerId)) return
            // Jedna strona inicjuje połączenie (mniejszy identyfikator), druga czeka – bez kolizji.
            // Gdy druga strona nas nie widzi, i tak łączymy się po chwili.
            if (myId < peerId) {
                request(endpointId)
            } else {
                main.postDelayed({ if (found.containsKey(endpointId) && !linkedTo(peerId)) request(endpointId) }, 7_000)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            found -= endpointId
            _nearby.value = found.size
        }
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            names[endpointId] = info.endpointName
            client.acceptConnection(endpointId, payloads)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            pending -= endpointId
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                val (peerId, name) = parse(names[endpointId] ?: "")
                val link = Link(endpointId, peerId, name, System.currentTimeMillis())
                _links.update { it + (endpointId to link) }
                listener.onLinkUp(link)
            } else {
                names -= endpointId
            }
        }

        override fun onDisconnected(endpointId: String) {
            drop(endpointId)
            rediscover()
        }
    }

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            listener.onBytes(endpointId, bytes)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    /** Aktualizacja nazwy sąsiada, gdy jego HELLO przyniesie nowszy nick. */
    fun rename(endpoint: String, peerId: String, name: String) = onMain {
        _links.update { m -> m[endpoint]?.let { m + (endpoint to it.copy(peerId = peerId, name = name)) } ?: m }
    }

    companion object {
        const val SERVICE_ID = "com.blacksingularities.openmesh.v1"
        const val MAX_PAYLOAD = 30 * 1024
    }
}
