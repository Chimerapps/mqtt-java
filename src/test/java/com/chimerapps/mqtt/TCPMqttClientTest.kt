package com.chimerapps.mqtt

import com.chimerapps.mqtt.format.MqttProtocolVersion
import com.chimerapps.mqtt.format.MqttQoS
import com.chimerapps.mqtt.format.packets.MqttConnectionResult
import okhttp3.HttpUrl
import org.junit.Test

class TCPMqttClientTest:BaseMqttClientTest() {

    @Test
    fun connect() {
        val client = MqttClient.Builder("LeClient")
                .url(HttpUrl.get("http://localhost:1883"))
                //.url(HttpUrl.get("http://broker.hivemq.com:1883"))
                .connectionType(MqttClient.ConnectionType.TCP)
                .protocolVersion(MqttProtocolVersion.VERSION_3_1_1)
                .build()

        testClient(client)
    }

}