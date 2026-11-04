package com.zen.partygame.net

import com.zen.partygame.ui.tr
import android.content.Context
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
import com.zen.partygame.game.PROTO
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** 近くの部屋（ホスト）の情報。Nearby のエンドポイント名に詰めて公開する */
data class RoomInfo(val endpointId: String, val code: String, val hostName: String)

sealed interface NetEvent {
    data class RoomFound(val room: RoomInfo) : NetEvent
    data class RoomLost(val endpointId: String) : NetEvent
    /** ホスト側：参加の申し込みが来た（受けるか断るかは呼び出し側が決める） */
    data class JoinRequest(val endpointId: String, val name: String, val proto: Int, val did: String = "", val admin: AdminProof? = null) : NetEvent
    data class Connected(val endpointId: String) : NetEvent
    data class ConnectFailed(val endpointId: String, val rejected: Boolean) : NetEvent
    data class Disconnected(val endpointId: String) : NetEvent
    data class Received(val endpointId: String, val bytes: ByteArray) : NetEvent
    data class Error(val message: String) : NetEvent
    /** オンライン：その部屋コードは使われていた */
    data object CodeTaken : NetEvent
    /** オンライン：その部屋コードの部屋がない */
    data object RoomNotFound : NetEvent
}

/** 通信のつなぎ方（近く = Nearby、オンライン = PeerJS）。どちらも同じできごとを出す */
interface Link {
    val events: SharedFlow<NetEvent>
    fun startHosting(roomCode: String, hostName: String)
    fun accept(endpointId: String)
    fun reject(endpointId: String)
    /** 近く：部屋の endpointId に、オンライン：部屋コードに申し込む */
    /** meta = 申し込みに付ける情報（JSON。端末番号・管理者の署名）、myId = 自分の接続ID（オンラインの管理者だけ使う） */
    fun requestJoin(endpointId: String, myName: String, meta: String = "{}", myId: String? = null)
    fun send(endpointIds: List<String>, bytes: ByteArray)
    fun disconnect(endpointId: String)
    fun stopAll()
}

/**
 * Nearby Connections（Bluetooth / Wi-Fi の直接通信）の薄い包み。サーバーは使わない。
 * ホストは1台、参加者はホストとだけつながる星型（P2P_STAR）。
 */
class NearbyLink(context: Context) : Link {
    private val client = Nearby.getConnectionsClient(context.applicationContext)
    private val serviceId = context.packageName
    private val strategy = Strategy.P2P_STAR
    private var isHost = false

    private val _events = MutableSharedFlow<NetEvent>(extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: SharedFlow<NetEvent> = _events

    private val chunks = Chunker()

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let { b -> chunks.accept(endpointId, b)?.let { _events.tryEmit(NetEvent.Received(endpointId, it)) } }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            if (isHost) {
                val (proto, name) = parseJoinName(info.endpointName)
                _events.tryEmit(NetEvent.JoinRequest(endpointId, name, proto))
            } else {
                client.acceptConnection(endpointId, payloadCallback)
            }
        }
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> _events.tryEmit(NetEvent.Connected(endpointId))
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> _events.tryEmit(NetEvent.ConnectFailed(endpointId, true))
                else -> _events.tryEmit(NetEvent.ConnectFailed(endpointId, false))
            }
        }
        override fun onDisconnected(endpointId: String) {
            chunks.forget(endpointId); _events.tryEmit(NetEvent.Disconnected(endpointId))
        }
    }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            parseRoomName(endpointId, info.endpointName)?.let { _events.tryEmit(NetEvent.RoomFound(it)) }
        }
        override fun onEndpointLost(endpointId: String) {
            _events.tryEmit(NetEvent.RoomLost(endpointId))
        }
    }

    override fun startHosting(roomCode: String, hostName: String) {
        isHost = true
        client.startAdvertising(
            "${PREFIX}|${PROTO}|${roomCode}|${hostName}", serviceId, lifecycle,
            AdvertisingOptions.Builder().setStrategy(strategy).build(),
        ).addOnFailureListener { _events.tryEmit(NetEvent.Error(describe(it))) }
    }

    fun stopAdvertising() = client.stopAdvertising()

    override fun accept(endpointId: String) {
        client.acceptConnection(endpointId, payloadCallback)
    }

    override fun reject(endpointId: String) {
        client.rejectConnection(endpointId)
    }

    fun startDiscovery() {
        isHost = false
        client.startDiscovery(serviceId, discovery, DiscoveryOptions.Builder().setStrategy(strategy).build())
            .addOnFailureListener { _events.tryEmit(NetEvent.Error(describe(it))) }
    }

    fun stopDiscovery() = client.stopDiscovery()

    override fun requestJoin(endpointId: String, myName: String, meta: String, myId: String?) {
        client.requestConnection("${PREFIX}|${PROTO}|${myName}", endpointId, lifecycle)
            .addOnFailureListener { _events.tryEmit(NetEvent.ConnectFailed(endpointId, false)) }
    }

    override fun send(endpointIds: List<String>, bytes: ByteArray) {
        if (endpointIds.isEmpty()) return
        // 1回で送れるのは 32KB まで。お絵かきの絵などで大きくなったら分けて送る
        for (part in chunks.split(bytes)) client.sendPayload(endpointIds, Payload.fromBytes(part))
    }

    override fun disconnect(endpointId: String) { chunks.forget(endpointId); client.disconnectFromEndpoint(endpointId) }

    override fun stopAll() {
        client.stopAdvertising(); client.stopDiscovery(); client.stopAllEndpoints()
    }

    private fun describe(e: Exception): String {
        val code = (e as? com.google.android.gms.common.api.ApiException)?.statusCode
        val name = code?.let { ConnectionsStatusCodes.getStatusCodeString(it) } ?: ""
        return when {
            code == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING ||
                code == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING -> tr("すでに通信中です")
            name.startsWith("MISSING_PERMISSION") -> tr("「付近のデバイス」の許可が必要です")
            else -> tr("近くの端末と通信できませんでした（BluetoothとWi-Fi、位置情報をオンにしてください）")
        }
    }

    companion object {
        private const val PREFIX = "PG"

        fun parseRoomName(endpointId: String, name: String): RoomInfo? {
            val parts = name.split("|", limit = 4)
            if (parts.size != 4 || parts[0] != PREFIX || parts[1].toIntOrNull() != PROTO) return null
            return RoomInfo(endpointId, parts[2], parts[3])
        }

        fun parseJoinName(name: String): Pair<Int, String> {
            val parts = name.split("|", limit = 3)
            if (parts.size != 3 || parts[0] != PREFIX) return -1 to name
            return (parts[1].toIntOrNull() ?: -1) to parts[2]
        }
    }
}
