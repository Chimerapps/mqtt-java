package com.chimerapps.mqtt.format.packets

import com.chimerapps.mqtt.StreamDataUtils
import com.chimerapps.mqtt.format.MqttControlPacketType
import com.chimerapps.mqtt.format.protocol.PacketStorage
import okio.BufferedSink
import okio.BufferedSource

internal interface MqttOutPacket {
    val type: MqttControlPacketType
    val flags: Int

    fun write(target: BufferedSink) {
        val header = (type.code shl 4) + flags
        target.writeByte(header)
        writePacket(target)
    }

    fun writePacket(target: BufferedSink)
}

internal interface MqttInPacket {
    val type: MqttControlPacketType
    val flags: Int
    val remainingLength: Long

    fun readPacket(buffer: BufferedSource, packetStorage: PacketStorage)
}

internal interface PacketIdAware {
    val packetIdentifier: Int
    fun setPackageIdentifier(packetIdentifier: Int): Boolean
}

internal interface MqttOutPIDPacket : MqttOutPacket, PacketIdAware
internal interface MqttInPIDPacket : MqttInPacket {
    val packetIdentifier: Int
    val linkedPacket: MqttOutPIDPacket?
}


internal abstract class AbstractMqttOutPacket(override val type: MqttControlPacketType, override val flags: Int) : MqttOutPacket

internal abstract class AbstractMqttInPacket(override val type: MqttControlPacketType, override val flags: Int, override val remainingLength: Long) : MqttInPacket

internal abstract class AbstractMqttOutPIDPacket(type: MqttControlPacketType, flags: Int) : AbstractMqttOutPacket(type, flags), MqttOutPIDPacket {

    private var _internalPacketIdentifier = -1

    override val packetIdentifier: Int
        get() = _internalPacketIdentifier

    override fun setPackageIdentifier(packetIdentifier: Int): Boolean {
        if (_internalPacketIdentifier != -1)
            throw IllegalStateException("Packet identifier has already been set")
        this._internalPacketIdentifier = packetIdentifier
        return true
    }

}

internal abstract class AbstractMqttInPIDPacket(type: MqttControlPacketType, flags: Int,
                                                remainingLength: Long) : AbstractMqttInPacket(type, flags, remainingLength), MqttInPIDPacket {

    private var _internalPacketIdentifier = -1
    private var _internalLinkedPacket: MqttOutPIDPacket? = null

    override val packetIdentifier: Int
        get() = _internalPacketIdentifier

    override val linkedPacket: MqttOutPIDPacket?
        get() = _internalLinkedPacket

    fun readPacketIdentifier(source: BufferedSource, packetStorage: PacketStorage, removeLinked: Boolean = true) {
        _internalPacketIdentifier = StreamDataUtils.readShort(source)
        _internalLinkedPacket = packetStorage.get(_internalPacketIdentifier, removeLinked)
    }
}