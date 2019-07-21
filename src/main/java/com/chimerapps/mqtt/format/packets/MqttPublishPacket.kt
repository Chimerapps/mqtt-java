package com.chimerapps.mqtt.format.packets

import com.chimerapps.mqtt.StreamDataUtils
import com.chimerapps.mqtt.format.FLAGS_PUBACK
import com.chimerapps.mqtt.format.FLAGS_PUBREC
import com.chimerapps.mqtt.format.FLAGS_PUBREL
import com.chimerapps.mqtt.format.MqttControlPacketType
import com.chimerapps.mqtt.format.MqttQoS
import com.chimerapps.mqtt.format.protocol.PacketStorage
import okio.BufferedSink
import okio.BufferedSource
import java.io.IOException

//SPEC 3.3
internal class MqttPublishOutPacket(private val topic: String, private val message: ByteArray,
                                    val qoS: MqttQoS, private val isDup: Boolean, private val retain: Boolean)
    : AbstractMqttOutPIDPacket(MqttControlPacketType.PUBLISH, 0) {

    override val flags: Int
        get() {
            if (qoS == MqttQoS.AT_MOST_ONCE) return 0
            return ((if (isDup) 1 else 0) shl 3) + (qoS.code shl 1) + (if (retain) 1 else 0)
        }


    override fun setPackageIdentifier(packetIdentifier: Int): Boolean {
        if (qoS == MqttQoS.AT_MOST_ONCE)
            return false

        return super.setPackageIdentifier(packetIdentifier)
    }

    override fun writePacket(target: BufferedSink) {
        //SPEC 3.3.2
        val topicBytes = topic.toByteArray(Charsets.UTF_8)
        var headerSize = 2 /* strlen */ + topicBytes.size + message.size
        if (packetIdentifier != -1)
            headerSize += 2

        StreamDataUtils.encodeVariableLength(target, headerSize.toLong())
        target.writeShort(topicBytes.size).write(topicBytes)

        if (packetIdentifier != -1)
            target.writeShort(packetIdentifier)
        target.write(message)
    }
}

//SPEC 3.3
internal class MqttPublishInPacket(flags: Int, remainingLength: Long)
    : AbstractMqttInPIDPacket(MqttControlPacketType.PUBLISH, flags, remainingLength) {

    lateinit var message: ByteArray
    lateinit var topic: String
    var isDup: Boolean = false
        private set
    var retain: Boolean = false
        private set
    lateinit var qoS: MqttQoS
        private set

    override fun readPacket(buffer: BufferedSource, packetStorage: PacketStorage) {
        isDup = (flags and 0x8) != 0
        val qosValue = (flags and 0x6) shr 1
        retain = (flags and 0x1) != 0

        qoS = MqttQoS.values().find { it.code == qosValue } ?: throw IOException("Unknown QOS value")

        val topicSize = StreamDataUtils.readShort(buffer)
        topic = buffer.readString(topicSize.toLong(), Charsets.UTF_8)

        var dataLen = remainingLength - (2 + topicSize)

        if (qosValue != 0) {
            readPacketIdentifier(buffer, packetStorage)
            dataLen -= 2
        }

        message = buffer.readByteArray(dataLen)
    }

    override fun toString(): String {
        return "MqttPublishInPacket(message=${message.toString(Charsets.UTF_8)}, topic='$topic', isDup=$isDup, retain=$retain, qoS=$qoS)"
    }

}

//SPEC 3.4
internal class MqttPubAckPacket(flags: Int, remainingLength: Long)
    : AbstractMqttInPIDPacket(MqttControlPacketType.PUBACK, flags, remainingLength) {

    override fun readPacket(buffer: BufferedSource, packetStorage: PacketStorage) {
        if (remainingLength != 2L)
            throw IOException("Unexpected remaining length")
        readPacketIdentifier(buffer, packetStorage)
    }
}

//SPEC 3.4
internal class MqttPubAckOutPacket(private val packetIdentifier: Int)
    : AbstractMqttOutPacket(MqttControlPacketType.PUBACK, FLAGS_PUBACK) {

    override fun writePacket(target: BufferedSink) {
        StreamDataUtils.encodeVariableLength(target, 2)
        target.writeShort(packetIdentifier)
    }
}

//SPEC 3.5
internal class MqttPubRecPacket(flags: Int, remainingLength: Long)
    : AbstractMqttInPIDPacket(MqttControlPacketType.PUBREC, flags, remainingLength) {

    override fun readPacket(buffer: BufferedSource, packetStorage: PacketStorage) {
        if (remainingLength != 2L)
            throw IOException("Unexpected remaining length")
        readPacketIdentifier(buffer, packetStorage, removeLinked = false)
    }
}

//SPEC 3.5
internal class MqttPubRecOutPacket(private val packetIdentifier: Int)
    : AbstractMqttOutPacket(MqttControlPacketType.PUBREC, FLAGS_PUBREC) {

    override fun writePacket(target: BufferedSink) {
        StreamDataUtils.encodeVariableLength(target, 2)
        target.writeShort(packetIdentifier)
    }
}

//SPEC 3.6
internal class MqttPubRelPacket(flags: Int, remainingLength: Long)
    : AbstractMqttInPIDPacket(MqttControlPacketType.PUBREL, flags, remainingLength) {

    override fun readPacket(buffer: BufferedSource, packetStorage: PacketStorage) {
        if (remainingLength != 2L)
            throw IOException("Unexpected remaining length")
        readPacketIdentifier(buffer, packetStorage, removeLinked = true)
    }
}

//SPEC 3.6
internal class MqttPubRelOutPacket(override val packetIdentifier: Int)
    : AbstractMqttOutPIDPacket(MqttControlPacketType.PUBREL, FLAGS_PUBREL) {

    override fun setPackageIdentifier(packetIdentifier: Int): Boolean {
        return true
    }

    override fun writePacket(target: BufferedSink) {
        StreamDataUtils.encodeVariableLength(target, 2)
        target.writeShort(packetIdentifier)
    }
}

//SPEC 3.7
internal class MqttPubCompPacket(flags: Int, remainingLength: Long)
    : AbstractMqttInPIDPacket(MqttControlPacketType.PUBCOMP, flags, remainingLength) {

    override fun readPacket(buffer: BufferedSource, packetStorage: PacketStorage) {
        if (remainingLength != 2L)
            throw IOException("Unexpected remaining length")
        readPacketIdentifier(buffer, packetStorage, removeLinked = false)
    }
}

//SPEC 3.7
internal class MqttPubCompOutPacket(private val packetIdentifier: Int)
    : AbstractMqttOutPacket(MqttControlPacketType.PUBCOMP, FLAGS_PUBREL) {

    override fun writePacket(target: BufferedSink) {
        StreamDataUtils.encodeVariableLength(target, 2)
        target.writeShort(packetIdentifier)
    }
}