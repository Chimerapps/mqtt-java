package com.chimerapps.mqtt.format

//SPEC 2.2.1
internal enum class MqttControlPacketType(val code: Int) {

    CONNECT(1),
    CONNACK(2),
    PUBLISH(3),
    PUBACK(4),
    PUBREC(5),
    PUBREL(6),
    PUBCOMP(7),
    SUBSCRIBE(8),
    SUBACK(9),
    UNSUBSCRIBE(10),
    UNSUBACK(11),
    PINGREQ(12),
    PINGRESP(13),
    DISCONNECT(14),
}

//SPEC 2.2.2
internal const val FLAGS_CONNECT = 0
internal const val FLAGS_CONNACK = 0

internal const val FLAGS_PUBLISH_DUP = 0b1000
internal const val FLAGS_PUBLISH_QOS1 = 0b0100
internal const val FLAGS_PUBLISH_QOS2 = 0b0010
internal const val FLAGS_PUBLISH_RETAIN = 0b0001

internal const val FLAGS_PUBACK = 0
internal const val FLAGS_PUBREC = 0
internal const val FLAGS_PUBREL = 0b0010
internal const val FLAGS_PUBCOMP = 0
internal const val FLAGS_SUBSCRIBE = 0b0010
internal const val FLAGS_SUBACK = 0
internal const val FLAGS_UNSUBSCRIBE = 0b0010
internal const val FLAGS_UNSUBACK = 0
internal const val FLAGS_PINGREQ = 0
internal const val FLAGS_PINGRESP = 0
internal const val FLAGS_DISCONNECT = 0

enum class MqttProtocolVersion(val code: Int) {
    VERSION_3_1_1(4),
    VERSION_3_1(3)
}

enum class MqttQoS(val code: Int) {
    AT_MOST_ONCE(0),
    AT_LEAST_ONCE(1),
    EXACTLY_ONCE(2)
}

internal data class MqttTopic(val topic: String, val qos: MqttQoS)