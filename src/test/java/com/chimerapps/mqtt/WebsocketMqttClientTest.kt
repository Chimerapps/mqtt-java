package com.chimerapps.mqtt

import com.chimerapps.mqtt.format.MqttProtocolVersion
import okhttp3.HttpUrl
import org.junit.Test

class WebsocketMqttClientTest : BaseMqttClientTest() {

    @Test
    fun connect() {
        val client = MqttClient.Builder("LeClient")
                .url(HttpUrl.get("http://broker.hivemq.com:8000/mqtt"))
                .connectionType(MqttClient.ConnectionType.WEBSOCKET)
                .protocolVersion(MqttProtocolVersion.VERSION_3_1_1)
                .build()

        testClient(client)
    }

}