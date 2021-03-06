package com.chimerapps.mqtt.connection

import com.chimerapps.mqtt.MqttClient
import okhttp3.HttpUrl
import okio.BufferedSource

internal interface MqttConnection {

    val connectionType: MqttClient.ConnectionType

    fun connect(url: HttpUrl, messageListener: MqttMessageListener)

    fun sendMqttMessage(buffer: BufferedSource)

    fun disconnect()

}

internal interface MqttMessageListener {

    fun onMessage(source: BufferedSource)

    fun onConnected()

    fun onClosed(code: Int, reason: String)

    fun onClosedWithError(error: Throwable)

}