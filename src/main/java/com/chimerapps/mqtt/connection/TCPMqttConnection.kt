package com.chimerapps.mqtt.connection

import okhttp3.Dns
import okhttp3.HttpUrl
import okio.BufferedSource
import okio.Sink
import okio.buffer
import okio.sink
import okio.source
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory
import javax.net.ssl.SSLSocketFactory

internal class TCPMqttConnection(private val normalSocketFactory: SocketFactory, private val sslSocketFactory: SSLSocketFactory) : MqttConnection {

    private companion object {
        private const val STATUS_IDLE = 0
        private const val STATUS_CONNECTING = 1
        private const val STATUS_CONNECTED = 2
    }

    private val lock = Any()
    private var socketHandler: TCPHandler? = null
    private var messageListener: MqttMessageListener? = null

    private var status = STATUS_IDLE

    override fun connect(url: HttpUrl, messageListener: MqttMessageListener) {
        synchronized(lock) {
            if (status != STATUS_IDLE)
                throw IllegalStateException("Connection is not idle")

            this.messageListener = messageListener

            status = STATUS_CONNECTING

            val handler = TCPHandler(url, normalSocketFactory, sslSocketFactory, this)
            socketHandler = handler
            Thread(handler, "TCP Mqtt Handler").start()
        }
    }

    override fun sendMqttMessage(buffer: BufferedSource) {
        synchronized(lock) {
            socketHandler?.write(buffer)
        }
    }

    override fun disconnect() {
        synchronized(lock) {
            socketHandler?.close()
            socketHandler = null
            val messageListenerCopy = messageListener
            messageListener = null
            status = STATUS_IDLE
            messageListenerCopy?.onClosed(1000, "")
        }
    }

    internal fun onError(handler: TCPHandler, error: Throwable) {
        synchronized(lock) {
            if (socketHandler != handler)
                return
            socketHandler = null
            val messageListenerCopy = messageListener
            messageListener = null
            status = STATUS_IDLE
            messageListenerCopy?.onClosedWithError(error)
        }
    }

    internal fun onMessage(handler: TCPHandler, data: BufferedSource) {
        synchronized(lock) {
            if (socketHandler != handler)
                return
            messageListener
        }?.onMessage(data)
    }

    internal fun onConnected(handler: TCPHandler) {
        synchronized(lock) {
            if (socketHandler != handler)
                return
            status = STATUS_CONNECTED
            messageListener?.onConnected()
        }
    }
}

internal class TCPHandler(private val url: HttpUrl, private val normalSocketFactory: SocketFactory,
                          private val sslSocketFactory: SSLSocketFactory, private val parent: TCPMqttConnection) : Runnable {

    private val concurrentWriteLock = Any()
    private var socketSink: Sink? = null
    private var socket: Socket? = null
    private var socketSource: BufferedSource? = null
    private var closed = false
    private var thread: Thread? = null

    override fun run() {
        synchronized(this) {
            if (closed)
                return
            thread = Thread.currentThread()
        }
        try {
            connect().use {
                runLoop(it)
            }
        } catch (e: Throwable) {
            synchronized(this) { if (closed) return }
            parent.onError(this, e)
        }
    }

    fun close() {
        synchronized(this) {
            synchronized(concurrentWriteLock) {
                closed = true
                socket?.close()
                socketSink = null
                socketSource = null
                socket = null
            }
            val temp = thread
            thread = null
            temp
        }?.interrupt()
    }

    fun write(data: BufferedSource) {
        synchronized(this) {
            synchronized(concurrentWriteLock) {
                socketSink?.let { sink ->
                    data.readAll(sink)
                    sink.flush()
                }
            }
        }
    }

    private fun runLoop(socket: Socket) {
        val input = synchronized(this) {
            this.socket = socket
            val input = socket.source().buffer()
            socketSource = input
            socketSink = socket.sink()
            input
        }
        parent.onConnected(this)
        while (socket.isConnected) {
            parent.onMessage(this, input) //Pretend we have a message
        }
    }

    private fun connect(): Socket {
        val ipAddresses = Dns.SYSTEM.lookup(url.host())
        val isSSL = when (url.scheme()) {
            "https", "wss" -> true
            else -> false
        }
        val port = url.port()
        return connectOnOne(ipAddresses, port, isSSL)
    }

    private fun connectOnOne(addresses: List<InetAddress>, port: Int, ssl: Boolean): Socket {
        var lastError: Throwable? = null
        addresses.forEach { address ->
            try {
                return if (ssl)
                    sslSocketFactory.createSocket(address, port)
                else
                    normalSocketFactory.createSocket(address, port)
            } catch (e: Throwable) {
                lastError = e
            }
        }
        lastError?.let { throw it }

        throw IOException("No addresses found to connect on")
    }

}