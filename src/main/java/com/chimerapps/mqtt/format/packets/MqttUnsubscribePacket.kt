package com.chimerapps.mqtt.format.packets

import com.chimerapps.mqtt.StreamDataUtils
import com.chimerapps.mqtt.format.FLAGS_UNSUBSCRIBE
import com.chimerapps.mqtt.format.MqttControlPacketType
import com.chimerapps.mqtt.format.protocol.PacketStorage
import okio.BufferedSink
import okio.BufferedSource
import java.io.IOException

//SPEC 3.10
internal class MqttUnsubscribePacket(val topics: List<String>)
    : AbstractMqttOutPIDPacket(MqttControlPacketType.UNSUBSCRIBE, FLAGS_UNSUBSCRIBE) {

    private companion object {
        private const val VARIABLE_HEADER_SIZE = 2
    }

    override fun writePacket(target: BufferedSink) {
        //SPEC 3.10.3
        val payloadBytes = topics.map { it.toByteArray(Charsets.UTF_8) }

        StreamDataUtils.encodeVariableLength(target, (VARIABLE_HEADER_SIZE + payloadBytes.sumBy { it.size + 2 }).toLong())

        //SPEC 3.10.2
        target.writeShort(packetIdentifier)

        //SPEC 3.10.3
        payloadBytes.forEach { bytes ->
            target.writeShort(bytes.size)
            target.write(bytes)
        }
    }
}

//SPEC 3.11
internal class MqttUnsubAckPacket(flags: Int, remainingLength: Long)
    : AbstractMqttInPIDPacket(MqttControlPacketType.UNSUBACK, flags, remainingLength) {

    override fun readPacket(buffer: BufferedSource, packetStorage: PacketStorage) {
        if (remainingLength != 2L)
            throw IOException("Remaining length not correct")
        readPacketIdentifier(buffer, packetStorage)
    }

}