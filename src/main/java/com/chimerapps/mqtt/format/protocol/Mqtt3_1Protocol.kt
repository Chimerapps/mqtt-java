package com.chimerapps.mqtt.format.protocol

import com.chimerapps.mqtt.StreamDataUtils
import com.chimerapps.mqtt.format.MqttControlPacketType
import com.chimerapps.mqtt.format.packets.MqttConnackPacket
import com.chimerapps.mqtt.format.packets.MqttInPacket
import com.chimerapps.mqtt.format.packets.MqttOutPIDPacket
import com.chimerapps.mqtt.format.packets.MqttOutPacket
import com.chimerapps.mqtt.format.packets.MqttPingRespPacket
import com.chimerapps.mqtt.format.packets.MqttPubAckPacket
import com.chimerapps.mqtt.format.packets.MqttPubCompPacket
import com.chimerapps.mqtt.format.packets.MqttPubRecPacket
import com.chimerapps.mqtt.format.packets.MqttPubRelPacket
import com.chimerapps.mqtt.format.packets.MqttPublishInPacket
import com.chimerapps.mqtt.format.packets.MqttSubAckPacket
import com.chimerapps.mqtt.format.packets.MqttUnsubAckPacket
import okio.Buffer
import okio.BufferedSource

internal open class Mqtt3_1Protocol : PacketStorage {

    private var packageIdCounter = 1
    private val pidMap = hashMapOf<Int, MqttOutPIDPacket>()

    fun onMessage(message: BufferedSource): MqttInPacket? {
        val controlByte = message.readByte().toInt()

        val type = (controlByte and 0xFF) shr 4
        val flags = controlByte and 0xF

        return handleMessage(type, flags, message)
    }

    fun buildMessage(message: MqttOutPacket): BufferedSource {
        if (message is MqttOutPIDPacket) {
            val pid = nextPackageId()
            if (message.setPackageIdentifier(pid)) {
                synchronized(pidMap) {
                    pidMap[pid] = message
                }
            }
        }

        val buffer = Buffer()
        message.write(buffer)
        return buffer
    }

    fun clear() {
        synchronized(this) {
            packageIdCounter = 1
        }
        synchronized(pidMap) {
            pidMap.clear()
        }
    }

    protected open fun handleMessage(type: Int, flags: Int, message: BufferedSource): MqttInPacket? {
        val remainingBytes = StreamDataUtils.readVariableLength(message)

        return when (type) {
            MqttControlPacketType.CONNACK.code -> handleConnectAck(flags, remainingBytes, message)
            MqttControlPacketType.SUBACK.code -> handleSubAck(flags, remainingBytes, message)
            MqttControlPacketType.PUBLISH.code -> handlePublish(flags, remainingBytes, message)
            MqttControlPacketType.PUBACK.code -> handlePubAck(flags, remainingBytes, message)
            MqttControlPacketType.PUBREC.code -> handlePubRec(flags, remainingBytes, message)
            MqttControlPacketType.PUBCOMP.code -> handlePubComp(flags, remainingBytes, message)
            MqttControlPacketType.UNSUBACK.code -> handleUnsubAck(flags, remainingBytes, message)
            MqttControlPacketType.PINGRESP.code -> handlePingResp(flags, remainingBytes, message)
            MqttControlPacketType.PUBREL.code -> handlePubRel(flags, remainingBytes, message)
            MqttControlPacketType.SUBSCRIBE.code,
            MqttControlPacketType.UNSUBSCRIBE.code,
            MqttControlPacketType.PINGREQ.code,
            MqttControlPacketType.DISCONNECT.code -> {
                println("Currently unhandled message type: $type")
                null
            }
            else -> {
                println("Currently unhandled message type: $type")
                null
            }
        }

    }

    protected open fun handleConnectAck(flags: Int, remainingBytes: Long, message: BufferedSource): MqttConnackPacket {
        return MqttConnackPacket(flags, remainingBytes).also { it.readPacket(message, packetStorage = this) }
    }

    protected open fun handleSubAck(flags: Int, remainingBytes: Long, message: BufferedSource): MqttSubAckPacket {
        return MqttSubAckPacket(flags, remainingBytes).also { it.readPacket(message, packetStorage = this) }
    }

    protected open fun handlePublish(flags: Int, remainingBytes: Long, message: BufferedSource): MqttPublishInPacket {
        return MqttPublishInPacket(flags, remainingBytes).also { it.readPacket(message, packetStorage = this) }
    }

    protected open fun handlePubAck(flags: Int, remainingBytes: Long, message: BufferedSource): MqttPubAckPacket {
        return MqttPubAckPacket(flags, remainingBytes).also { it.readPacket(message, packetStorage = this) }
    }

    protected open fun handlePubRec(flags: Int, remainingBytes: Long, message: BufferedSource): MqttPubRecPacket {
        return MqttPubRecPacket(flags, remainingBytes).also { it.readPacket(message, packetStorage = this) }
    }

    protected open fun handlePubComp(flags: Int, remainingBytes: Long, message: BufferedSource): MqttPubCompPacket {
        return MqttPubCompPacket(flags, remainingBytes).also { it.readPacket(message, packetStorage = this) }
    }

    protected open fun handleUnsubAck(flags: Int, remainingBytes: Long, message: BufferedSource): MqttUnsubAckPacket {
        return MqttUnsubAckPacket(flags, remainingBytes).also { it.readPacket(message, packetStorage = this) }
    }

    protected open fun handlePingResp(flags: Int, remainingBytes: Long, message: BufferedSource): MqttPingRespPacket {
        return MqttPingRespPacket(flags, remainingBytes).also { it.readPacket(message, packetStorage = this) }
    }

    protected open fun handlePubRel(flags: Int, remainingBytes: Long, message: BufferedSource): MqttPubRelPacket {
        return MqttPubRelPacket(flags, remainingBytes).also { it.readPacket(message, packetStorage = this) }
    }

    protected fun nextPackageId(): Int {
        synchronized(this) {
            return packageIdCounter++
        }
    }

    override fun get(packetId: Int, remove: Boolean): MqttOutPIDPacket? {
        return synchronized(pidMap) { if (remove) pidMap.remove(packetId) else pidMap[packetId] }
    }
}

internal interface PacketStorage {

    fun get(packetId: Int, remove: Boolean = true): MqttOutPIDPacket?

}