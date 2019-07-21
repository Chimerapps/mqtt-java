package com.chimerapps.mqtt.format.packets

import com.chimerapps.mqtt.StreamDataUtils
import com.chimerapps.mqtt.format.FLAGS_SUBSCRIBE
import com.chimerapps.mqtt.format.MqttControlPacketType
import com.chimerapps.mqtt.format.MqttQoS
import com.chimerapps.mqtt.format.MqttTopic
import com.chimerapps.mqtt.format.protocol.PacketStorage
import okio.BufferedSink
import okio.BufferedSource
import java.io.IOException

//SPEC 3.8
internal class MqttSubscribePacket(val topics: List<MqttTopic>) : AbstractMqttOutPIDPacket(MqttControlPacketType.SUBSCRIBE, FLAGS_SUBSCRIBE) {

    private companion object {
        private const val VARIABLE_HEADER_SIZE = 2
    }

    override fun writePacket(target: BufferedSink) {
        //SPEC 3.8.3
        val payloadBytes = topics.map { it.topic.toByteArray(Charsets.UTF_8) }

        StreamDataUtils.encodeVariableLength(target, (VARIABLE_HEADER_SIZE + payloadBytes.sumBy { it.size + 3 }).toLong())

        //SPEC 3.8.2.1
        target.writeShort(packetIdentifier)

        //SPEC 3.8.3
        topics.forEachIndexed { index, mqttTopic ->
            val bytes = payloadBytes[index]
            target.writeShort(bytes.size)
            target.write(bytes)
            target.writeByte(mqttTopic.qos.code)
        }
    }

}

//SPEC 3.9
internal class MqttSubAckPacket(flags: Int, remainingLength: Long)
    : AbstractMqttInPIDPacket(MqttControlPacketType.SUBACK, flags, remainingLength) {

    lateinit var acceptedTopics: List<MqttTopic>
        private set

    override fun readPacket(buffer: BufferedSource, packetStorage: PacketStorage) {
        if (remainingLength < 2) {
            throw IOException("Remaining length not correct")
        }
        readPacketIdentifier(buffer, packetStorage)

        val linked = linkedPacket as? MqttSubscribePacket ?: throw IOException("Linked packed is not a subscribe request")

        if (remainingLength != (2L + linked.topics.size))
            throw IOException("Wrong remaining packet length, expected ${2 + linked.topics.size} but got $remainingLength")

        val acceptedTopics = mutableListOf<MqttTopic>()
        linked.topics.forEach { originalTopic ->
            val result = buffer.readByte().toInt()
            MqttQoS.values().find { qos -> qos.code == result }?.let { acceptedTopics += originalTopic.copy(qos = it) }
        }
        this.acceptedTopics = acceptedTopics
    }

    override fun toString(): String {
        return "MqttSubAckPacket(packetId=$packetIdentifier, acceptedTopics=$acceptedTopics)"
    }

}