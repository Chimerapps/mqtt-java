package com.chimerapps.mqtt.format.packets

import com.chimerapps.mqtt.StreamDataUtils
import com.chimerapps.mqtt.format.FLAGS_PINGREQ
import com.chimerapps.mqtt.format.MqttControlPacketType
import com.chimerapps.mqtt.format.protocol.PacketStorage
import okio.BufferedSink
import okio.BufferedSource
import java.io.IOException

//SPEC 3.12
internal class MqttPingPacket : AbstractMqttOutPacket(MqttControlPacketType.PINGREQ, FLAGS_PINGREQ) {
    override fun writePacket(target: BufferedSink) {
        StreamDataUtils.encodeVariableLength(target, 0)
    }
}

//SPEC 3.13
internal class MqttPingRespPacket(flags: Int, remainingLength: Long)
    : AbstractMqttInPacket(MqttControlPacketType.PINGRESP, flags, remainingLength) {
    override fun readPacket(buffer: BufferedSource, packetStorage: PacketStorage) {
        if (remainingLength != 0L)
            throw IOException("Remaining length not correct")
    }
}