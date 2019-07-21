package com.chimerapps.mqtt

import com.chimerapps.mqtt.connection.MqttConnection
import com.chimerapps.mqtt.connection.MqttMessageListener
import com.chimerapps.mqtt.connection.TCPMqttConnection
import com.chimerapps.mqtt.connection.WebsocketMqttConnection
import com.chimerapps.mqtt.format.MqttProtocolVersion
import com.chimerapps.mqtt.format.MqttQoS
import com.chimerapps.mqtt.format.MqttTopic
import com.chimerapps.mqtt.format.packets.MqttConnackPacket
import com.chimerapps.mqtt.format.packets.MqttConnectPacket
import com.chimerapps.mqtt.format.packets.MqttConnectionResult
import com.chimerapps.mqtt.format.packets.MqttDisconnectPacket
import com.chimerapps.mqtt.format.packets.MqttPingPacket
import com.chimerapps.mqtt.format.packets.MqttPingRespPacket
import com.chimerapps.mqtt.format.packets.MqttPubAckOutPacket
import com.chimerapps.mqtt.format.packets.MqttPubCompOutPacket
import com.chimerapps.mqtt.format.packets.MqttPubCompPacket
import com.chimerapps.mqtt.format.packets.MqttPubRecOutPacket
import com.chimerapps.mqtt.format.packets.MqttPubRecPacket
import com.chimerapps.mqtt.format.packets.MqttPubRelOutPacket
import com.chimerapps.mqtt.format.packets.MqttPubRelPacket
import com.chimerapps.mqtt.format.packets.MqttPublishInPacket
import com.chimerapps.mqtt.format.packets.MqttPublishOutPacket
import com.chimerapps.mqtt.format.packets.MqttSubAckPacket
import com.chimerapps.mqtt.format.packets.MqttSubscribePacket
import com.chimerapps.mqtt.format.packets.MqttUnsubAckPacket
import com.chimerapps.mqtt.format.packets.MqttUnsubscribePacket
import com.chimerapps.mqtt.format.protocol.Mqtt3_1Protocol
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okio.BufferedSource
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.net.SocketFactory
import javax.net.ssl.SSLSocketFactory

class MqttClient private constructor(private val url: HttpUrl,
                                     private val clientId: String,
                                     private val username: String?,
                                     private val password: String?,
                                     private val willTopic: String?,
                                     private val willMessage: String?,
                                     private val willQoS: MqttQoS?,
                                     private val retainWill: Boolean,
                                     private val cleanSession: Boolean,
                                     private val protocolVersion: MqttProtocolVersion,
                                     private val connection: MqttConnection,
                                     private val callbackExecutor: Executor) {

    enum class ConnectionType {
        TCP, WEBSOCKET
    }

    private val protocolHandler = Mqtt3_1Protocol() //No special handling required for 3.1.1

    /**
     * Connect to the server and use the given listener to report messages on
     *
     * @param listener  The listener to use for callbacks
     */
    fun connect(listener: MqttClientListener) {
        connection.connect(url, object : MqttMessageListener {
            override fun onMessage(source: BufferedSource) {
                when (val parsed = protocolHandler.onMessage(source)) {
                    is MqttConnackPacket -> {
                        when (val connectionStatus = parsed.connectionStatus) {
                            MqttConnectionResult.ACCEPTED -> callbackExecutor.execute { listener.onConnected() }
                            else -> callbackExecutor.execute { listener.onConnectionFailed(connectionStatus ?: MqttConnectionResult.UNKNOWN) }
                        }
                    }
                    is MqttPublishInPacket -> {
                        callbackExecutor.execute { listener.onMessage(MqttMessage.create(parsed)) }
                        when (parsed.qoS) {
                            MqttQoS.AT_MOST_ONCE -> Unit //Do nothing
                            MqttQoS.AT_LEAST_ONCE -> connection.sendMqttMessage(protocolHandler.buildMessage(MqttPubAckOutPacket(parsed.packetIdentifier)))
                            MqttQoS.EXACTLY_ONCE -> connection.sendMqttMessage(protocolHandler.buildMessage(MqttPubRecOutPacket(parsed.packetIdentifier)))
                        }
                    }
                    is MqttPingRespPacket -> callbackExecutor.execute { listener.onPong() }
                    is MqttPubRelPacket -> connection.sendMqttMessage(protocolHandler.buildMessage(MqttPubCompOutPacket(parsed.packetIdentifier)))
                    is MqttPubRecPacket -> connection.sendMqttMessage(protocolHandler.buildMessage(MqttPubRelOutPacket(parsed.packetIdentifier)))
                    is MqttSubAckPacket -> callbackExecutor.execute { listener.onActionSuccess(MqttToken(parsed.packetIdentifier)) }
                    is MqttUnsubAckPacket -> callbackExecutor.execute { listener.onActionSuccess(MqttToken(parsed.packetIdentifier)) }
                    is MqttPubCompPacket -> callbackExecutor.execute { listener.onActionSuccess(MqttToken(parsed.packetIdentifier)) }
                }
            }

            override fun onConnected() {
                val buffer = protocolHandler.buildMessage(MqttConnectPacket(protocolVersion, clientId, username, password,
                        willTopic, willMessage, willQoS, retainWill, cleanSession))
                connection.sendMqttMessage(buffer)
            }

            override fun onClosed(code: Int, reason: String) {
                callbackExecutor.execute { listener.onDisconnected(null) }
                protocolHandler.clear()
            }

            override fun onClosedWithError(error: Throwable) {
                callbackExecutor.execute { listener.onDisconnected(error) }
                protocolHandler.clear()
            }
        })
    }

    /**
     * Disconnects cleanly from the server
     */
    fun disconnect() {
        connection.sendMqttMessage(protocolHandler.buildMessage(MqttDisconnectPacket()))
        connection.disconnect()
    }

    /**
     * Subscribes to the given topic and request the given QoS
     *
     * @param topic The topic to subscribe to
     * @param requestQoS    The requested QoS for this subscription
     * @return A subscription helper that can be used to easily unsubscribe from this topic. Also contains the token which can be used to report the status of the subscription
     */
    fun subscribe(topic: String, requestQoS: MqttQoS): MqttTopicSubscription {
        val packet = MqttSubscribePacket(listOf(MqttTopic(topic, requestQoS)))
        val blob = protocolHandler.buildMessage(packet)

        connection.sendMqttMessage(blob)
        return MqttTopicSubscription(topic, this, MqttToken(packet.packetIdentifier))
    }

    /**
     * Unsubscribes from the given topic
     *
     * @param topic The topic to unsubscribe from
     * @return A token which can be used to report the status of the unsubscribe
     */
    fun unsubscribe(topic: String): MqttToken {
        val unsubscribe = MqttUnsubscribePacket(listOf(topic))
        connection.sendMqttMessage(protocolHandler.buildMessage(unsubscribe))
        return MqttToken(unsubscribe.packetIdentifier)
    }

    /**
     * Publishes the given message to the given topic with the provided QoS.
     *
     * See the MQTT specifications, chapter 3.3 for details about the parameters
     *
     * @param topic The topic to publish to
     * @param message   The message to publish
     * @param qos   The QoS to use for this publish
     * @param isDup Flag indicating that this message is a (probable) duplicate
     * @param retain    Flag indicating if this message should be retained
     * @return A token which can be used to report the status of the publication
     */
    fun publish(topic: String, message: ByteArray, qos: MqttQoS, isDup: Boolean, retain: Boolean): MqttToken {
        val msg = MqttPublishOutPacket(topic, message, qos, isDup, retain)
        val mqttMessage = protocolHandler.buildMessage(msg)

        connection.sendMqttMessage(mqttMessage)
        return MqttToken(msg.packetIdentifier)
    }

    /**
     * Send a ping to the server
     */
    fun ping() {
        connection.sendMqttMessage(protocolHandler.buildMessage(MqttPingPacket()))
    }

    class Builder(val clientId: String) {

        var connectionType = ConnectionType.TCP
            private set
        var httpUrl: HttpUrl? = null
            private set
        var okHttpClient: OkHttpClient? = null
            private set
        var username: String? = null
            private set
        var password: String? = null
            private set
        var willTopic: String? = null
            private set
        var willMessage: String? = null
            private set
        var willQoS: MqttQoS? = null
            private set
        var retainWill: Boolean = false
            private set
        var protocolVersion: MqttProtocolVersion = MqttProtocolVersion.VERSION_3_1
            private set
        var cleanSession: Boolean = true
            private set
        var socketFactory: SocketFactory? = null
            private set
        var sslSocketFactory: SSLSocketFactory? = null
            private set
        var callbackExecutor: Executor? = null
            private set

        fun url(httpUrl: HttpUrl): Builder {
            this.httpUrl = httpUrl
            return this
        }

        fun connectionType(type: ConnectionType): Builder {
            connectionType = type
            return this
        }

        fun lastWill(topic: String, message: String, qos: MqttQoS, retain: Boolean): Builder {
            willTopic = topic
            willMessage = message
            willQoS = qos
            retainWill = retain
            return this
        }

        fun websocketClient(okHttpClient: OkHttpClient): Builder {
            this.okHttpClient = okHttpClient
            return this
        }

        fun tcpSocketFactory(socketFactory: SocketFactory, sslSocketFactory: SSLSocketFactory): Builder {
            this.socketFactory = socketFactory
            this.sslSocketFactory = sslSocketFactory
            return this
        }

        fun protocolVersion(version: MqttProtocolVersion): Builder {
            this.protocolVersion = version
            return this
        }

        fun credentials(username: String?, password: String?): Builder {
            this.username = username
            this.password = password
            return this
        }

        fun cleanSession(cleanSession: Boolean): Builder {
            this.cleanSession = cleanSession
            return this
        }

        fun callbackExecutor(executor: Executor): Builder {
            this.callbackExecutor = executor
            return this
        }

        fun build(): MqttClient {
            val url = httpUrl ?: throw IllegalStateException("Connection url not set")

            return MqttClient(
                    url,
                    clientId = clientId,
                    username = username,
                    password = password,
                    willTopic = willTopic,
                    willMessage = willMessage,
                    willQoS = willQoS,
                    retainWill = retainWill,
                    cleanSession = cleanSession,
                    protocolVersion = protocolVersion,
                    connection = createConnection(),
                    callbackExecutor = callbackExecutor ?: Executors.newSingleThreadExecutor()
            )
        }

        private fun createConnection(): MqttConnection {
            return when (connectionType) {
                ConnectionType.TCP -> TCPMqttConnection(normalSocketFactory = socketFactory ?: SocketFactory.getDefault(),
                        sslSocketFactory = sslSocketFactory ?: SSLSocketFactory.getDefault() as SSLSocketFactory)
                ConnectionType.WEBSOCKET -> WebsocketMqttConnection(okHttpClient ?: OkHttpClient.Builder().build())
            }
        }
    }

}

/**
 * Listener interface for mqtt messages. All callbacks are executed on the provided executor (see {@link MqttClient#Builder#callbackExecutor})
 */
interface MqttClientListener {

    /**
     * Called when the connection has been established and the server acknowledged the connection request
     */
    fun onConnected()

    /**
     * Called creating the connection has failed
     *
     * @param result    The reason for the connection failure
     */
    fun onConnectionFailed(result: MqttConnectionResult)

    /**
     * Called when the client has disconnected, optionally with error
     *
     * @param error Set when the client disconnected with an error, null if it disconnected normally
     */
    fun onDisconnected(error: Throwable?)

    /**
     * Called when a topic message is received
     *
     * @param message   The message sent to the topic
     */
    fun onMessage(message: MqttMessage)

    /**
     * Called when an action was confirmed by the server, use the token to associate this with the action
     *
     * @param token The token associated with the action
     */
    fun onActionSuccess(token: MqttToken)

    /**
     * Called when a server pong is received
     */
    fun onPong()

}

/**
 * Represents a topic message sent by a client
 */
data class MqttMessage(val topic: String, val payload: ByteArray, val qos: MqttQoS, val retain: Boolean, val isDup: Boolean) {

    internal companion object {
        internal fun create(packet: MqttPublishInPacket): MqttMessage {
            return MqttMessage(packet.topic, packet.message, packet.qoS, packet.retain, packet.isDup)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MqttMessage

        if (topic != other.topic) return false
        if (!payload.contentEquals(other.payload)) return false
        if (qos != other.qos) return false
        if (retain != other.retain) return false
        if (isDup != other.isDup) return false

        return true
    }

    override fun hashCode(): Int {
        var result = topic.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + qos.hashCode()
        result = 31 * result + retain.hashCode()
        result = 31 * result + isDup.hashCode()
        return result
    }

}

/**
 * Holder for a topic subscription
 */
class MqttTopicSubscription(private val topic: String, private val client: MqttClient, val token: MqttToken) {

    /**
     * Cancel the subscription. Note that due to threading parallelism, messages for this topic can still arrive for a moment after this method is called
     */
    fun cancel() {
        client.unsubscribe(topic)
    }

}

/**
 * Represents an action on the mqtt layer. Some actions return a token which will also be passed in {@link MqttClientListener#onActionSuccess}.
 *
 * NOTE: Use {Object#equals} and not pointer equality
 */
data class MqttToken(private val code: Int)