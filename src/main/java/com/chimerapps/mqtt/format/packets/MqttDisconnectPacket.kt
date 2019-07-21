package com.chimerapps.mqtt.format.packets

import com.chimerapps.mqtt.StreamDataUtils
import com.chimerapps.mqtt.format.FLAGS_DISCONNECT
import com.chimerapps.mqtt.format.MqttControlPacketType
import okio.BufferedSink

//SPEC 3.14
internal class MqttDisconnectPacket : AbstractMqttOutPacket(MqttControlPacketType.DISCONNECT, FLAGS_DISCONNECT) {
    override fun writePacket(target: BufferedSink) {
        StreamDataUtils.encodeVariableLength(target, 0)
    }
}