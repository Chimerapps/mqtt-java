package com.chimerapps.mqtt.connection

import com.chimerapps.mqtt.MqttClient
import com.chimerapps.mqtt.wrap
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.BufferedSource
import okio.ByteString

internal class WebsocketMqttConnection(internal val httpClient: OkHttpClient) : MqttConnection {

    private companion object {
        private const val STATUS_IDLE = 0
        private const val STATUS_CONNECTING = 1
        private const val STATUS_CONNECTED = 2
    }

    private val lock = Any()
    private var socketListener: MqttWebSocketListener? = null
    private var messageListener: MqttMessageListener? = null
    private var status = STATUS_IDLE

    override val connectionType: MqttClient.ConnectionType = MqttClient.ConnectionType.WEBSOCKET

    override fun connect(url: HttpUrl, messageListener: MqttMessageListener) {
        synchronized(lock) {
            if (status != STATUS_IDLE)
                throw IllegalStateException("Connection is not idle")

            val request = Request.Builder().url(url).build()
            val socketListener = MqttWebSocketListener(this)
            this.socketListener = socketListener
            this.messageListener = messageListener

            httpClient.newWebSocket(request, socketListener)

            status = STATUS_CONNECTING
        }
    }

    override fun disconnect() {
        synchronized(lock) {
            socketListener?.close()
            socketListener = null
            val messageListenerCopy = messageListener
            messageListener = null
            status = STATUS_IDLE
            messageListenerCopy?.onClosed(1000, "")
        }
    }

    override fun sendMqttMessage(buffer: BufferedSource) {
        synchronized(lock) {
            socketListener?.send(buffer)
        }
    }

    internal fun onMessage(listener: MqttWebSocketListener, data: BufferedSource) {
        synchronized(lock) {
            if (socketListener != listener)
                return
            messageListener?.onMessage(data)
        }
    }

    internal fun onConnected(listener: MqttWebSocketListener) {
        synchronized(lock) {
            if (socketListener != listener)
                return
            status = STATUS_CONNECTED
            messageListener?.onConnected()
        }
    }

    internal fun onConnectionClosed(listener: MqttWebSocketListener, error: Throwable) {
        synchronized(lock) {
            if (socketListener != listener)
                return
            socketListener = null
            val messageListenerCopy = messageListener
            messageListener = null
            status = STATUS_IDLE
            messageListenerCopy?.onClosedWithError(error)
        }
    }

    internal fun onConnectionClosed(listener: MqttWebSocketListener, code: Int, reason: String) {
        synchronized(lock) {
            if (socketListener != listener)
                return
            socketListener = null
            val messageListenerCopy = messageListener
            messageListener = null
            status = STATUS_IDLE
            messageListenerCopy?.onClosed(code, reason)
        }
    }

}

internal class MqttWebSocketListener(private val connection: WebsocketMqttConnection) : WebSocketListener() {

    private var socket: WebSocket? = null
    private var closed = false

    internal fun close() {
        synchronized(this) {
            closed = true
            socket?.close(1000, "")
            socket = null
        }
    }

    internal fun send(data: BufferedSource) {
        synchronized(this) {
            val blob = data.readByteString()
            socket?.send(blob)
        }
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        synchronized(this) {
            if (closed) {
                webSocket.close(1000, "")
                return
            }

            this.socket = webSocket
        }
        connection.onConnected(this)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        synchronized(this) {
            this.socket = null
        }
        connection.onConnectionClosed(this, t)
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        super.onMessage(webSocket, bytes)

        val buffer = bytes.toByteArray().wrap()
        connection.onMessage(this, buffer)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        synchronized(this) {
            this.socket = null
        }
        connection.onConnectionClosed(this, code, reason)
    }
}