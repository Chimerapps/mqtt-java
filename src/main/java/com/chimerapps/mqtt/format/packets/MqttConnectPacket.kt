package com.chimerapps.mqtt.format.packets

import com.chimerapps.mqtt.StreamDataUtils
import com.chimerapps.mqtt.format.FLAGS_CONNECT
import com.chimerapps.mqtt.format.MqttControlPacketType
import com.chimerapps.mqtt.format.MqttProtocolVersion
import com.chimerapps.mqtt.format.MqttQoS
import com.chimerapps.mqtt.format.protocol.PacketStorage
import okio.BufferedSink
import okio.BufferedSource
import java.io.IOException


//SPEC 3.1
internal open class MqttConnectPacket(private val protocolVersion: MqttProtocolVersion,
                                      private val clientIdentifier: String,
                                      private val username: String?,
                                      private val password: String?,
                                      private val willTopic: String?,
                                      private val willMessage: ByteArray?,
                                      private val willQoS: MqttQoS?,
                                      private val willRetain: Boolean,
                                      private val cleanSession: Boolean) : AbstractMqttOutPacket(MqttControlPacketType.CONNECT, FLAGS_CONNECT) {

    private companion object {
        private const val VARIABLE_HEADER_SIZE_3_1_1 = 10
        private const val VARIABLE_HEADER_SIZE_3_1 = 12

        //SPEC 3.1.2.1
        private val VARIABLE_HEADER_HEADER_3_1_1 = byteArrayOf(0, 4, 0x4D, 0x51, 0x54, 0x54)
        private val VARIABLE_HEADER_HEADER_3_1 = byteArrayOf(0, 6, 0x4D, 0x51, 0x49, 0x73, 0x64, 0x70)
    }

    override fun writePacket(target: BufferedSink) {
        val header = makeHeader()

        when (protocolVersion) {
            MqttProtocolVersion.VERSION_3_1_1 -> StreamDataUtils.encodeVariableLength(target, (VARIABLE_HEADER_SIZE_3_1_1 + header.payloadSize).toLong())
            MqttProtocolVersion.VERSION_3_1 -> StreamDataUtils.encodeVariableLength(target, (VARIABLE_HEADER_SIZE_3_1 + header.payloadSize).toLong())
        }

        writeHeader(target, header)
    }

    private fun writeHeader(target: BufferedSink, header: MqttConnectHeader) {
        //SPEC 3.1.2.1
        when (protocolVersion) {
            MqttProtocolVersion.VERSION_3_1_1 -> target.write(VARIABLE_HEADER_HEADER_3_1_1)
            MqttProtocolVersion.VERSION_3_1 -> target.write(VARIABLE_HEADER_HEADER_3_1)
        }

        //SPEC 3.1.2.2
        target.writeByte(header.protocolLevelFlag)

        //SPEC 3.1.2.3-9
        target.writeByte(header.connectFlags)

        //SPEC 3.1.2.10
        target.writeShort(header.keepAlive)

        target.writeShort(header.clientIdentifierBytes.size).write(header.clientIdentifierBytes)
        header.willTopicBytes?.let { target.writeShort(it.size).write(it) }
        header.willMessageBytes?.let { target.writeShort(it.size).write(it) }
        header.userNameBytes?.let { target.writeShort(it.size).write(it) }
        header.passwordBytes?.let { target.writeShort(it.size).write(it) }
    }

    //SPEC 3.1.2.2-10
    private fun makeHeader(): MqttConnectHeader {
        var connectFlags = 0
        val clientIdentifierBytes = clientIdentifier.toByteArray(Charsets.UTF_8)
        val willTopicBytes = willTopic?.toByteArray(Charsets.UTF_8)
        val usernameBytes = username?.toByteArray(Charsets.UTF_8)
        val passwordBytes = password?.toByteArray(Charsets.UTF_8)

        //SPEC 3.1.2.4
        if (cleanSession)
            connectFlags = connectFlags or 0b10

        //SPEC 3.1.2.5
        if (willTopic != null) {
            if (willMessage == null) {
                throw IOException("No will message set for topic")
            }
            connectFlags = connectFlags or 0b100

            //SPEC 3.1.2.6
            if (willQoS == null)
                throw IOException("Will set but QoS not set")
            connectFlags = connectFlags or ((willQoS.code and 0xFF) shl 3)

            //SPEC 3.1.2.7
            if (willRetain)
                connectFlags = connectFlags or 0b100000
        } else if (willMessage != null) {
            throw IOException("No will topic defined but message is set")
        }

        //SPEC 3.1.2.8
        if (username != null)
            connectFlags = connectFlags or 0b10000000

        //SPEC 3.1.2.9
        if (password != null) {
            if (username == null)
                throw IOException("Password is set but username is not")
            connectFlags = connectFlags or 0b1000000
        }

        val payloadSize = (2 + clientIdentifierBytes.size) +
                (willTopicBytes?.let { 2 + it.size } ?: 0) +
                (willMessage?.let { 2 + it.size } ?: 0) +
                (usernameBytes?.let { 2 + it.size } ?: 0) +
                (passwordBytes?.let { 2 + it.size } ?: 0)

        //TODO keepalive
        return MqttConnectHeader(protocolVersion.code, connectFlags, keepAlive = 0, payloadSize = payloadSize, clientIdentifierBytes = clientIdentifierBytes,
                willTopicBytes = willTopicBytes, willMessageBytes = willMessage, userNameBytes = usernameBytes, passwordBytes = passwordBytes)
    }

    private class MqttConnectHeader(val protocolLevelFlag: Int, val connectFlags: Int, val keepAlive: Int, val payloadSize: Int,
                                    val clientIdentifierBytes: ByteArray, val willTopicBytes: ByteArray?, val willMessageBytes: ByteArray?, val userNameBytes: ByteArray?,
                                    val passwordBytes: ByteArray?)

}

//SPEC 3.2
internal class MqttConnackPacket(flags: Int, remainingLength: Long) : AbstractMqttInPacket(MqttControlPacketType.CONNACK, flags, remainingLength) {

    var hasServerSession: Boolean = false
        private set
    var connectionStatus: MqttConnectionResult? = null
        private set

    override fun readPacket(buffer: BufferedSource, packetStorage: PacketStorage) {
        if (remainingLength != 2L)
            throw IOException("Malformed CONNACK packet, remaining length != 2")

        //SPEC 3.2.2.2
        val ackFlags = buffer.readByte().toInt()
        hasServerSession = (ackFlags and 0b1) == 0b1

        //SPEC 3.2.2.3
        val returnCode = buffer.readByte().toInt()

        connectionStatus = MqttConnectionResult.values().find { it.code == returnCode }
    }

    override fun toString(): String {
        return "MqttConnackPacket(hasServerSession=$hasServerSession, connectionStatus=$connectionStatus)"
    }
}

enum class MqttConnectionResult(internal val code: Int) {
    ACCEPTED(0),
    REFUSED_UNSUPPORTED_PROTOCOL(1),
    REFUSED_IDENTIFIER_REJECTED(2),
    REFUSED_SERVER_UNAVAILABLE(3),
    REFUSED_AUTH_FAILED(4),
    REFUSED_NOT_AUTHORIZED(5),
    UNKNOWN(-1)
}