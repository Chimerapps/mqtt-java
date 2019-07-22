package com.chimerapps.mqtt

import com.chimerapps.mqtt.format.MqttQoS
import com.chimerapps.mqtt.format.packets.MqttConnectionResult

open class BaseMqttClientTest {

    protected fun testClient(client: MqttClient) {
        client.connect(object : MqttClientListener {
            override fun onConnected(client: MqttClient) {
                println("Connected to mqtt, subscribing to topic")
                val token = client.subscribe("/testTopic", MqttQoS.AT_MOST_ONCE).token
                println("Subscribed with token: $token")
                println("Sending ping")
                client.ping()

                Thread.sleep(1000L)
                val pubToken = client.publish("/testTopic", "I like turtles".toByteArray(), qos = MqttQoS.EXACTLY_ONCE, isDup = false, retain = false)
                println("Got pub token: $pubToken")
            }

            override fun onConnectionFailed(client: MqttClient, result: MqttConnectionResult) {
                println("Connection failed: $result")
            }

            override fun onDisconnected(error: Throwable?) {
                println("Disconnected with error: $error")
                error?.printStackTrace()
            }

            override fun onMessage(message: MqttMessage) {
                println("Got message from topic: ${message.topic} -> ${message.payload.toString(Charsets.UTF_8)}")
            }

            override fun onActionSuccess(token: MqttToken) {
                println("Action $token is a success")
            }

            override fun onPong() {
                println("Got pong")
            }
        })

        println("Sleeping")
        Thread.sleep(4000L)

        println("Disconnecting")
        client.disconnect()

        println("Waiting")

        Thread.sleep(2000L)
    }


}