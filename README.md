# mqtt-java
[![Download](https://api.bintray.com/packages/nicolaverbeeck/maven/mqtt-java/images/download.svg)](https://bintray.com/nicolaverbeeck/maven/mqtt-java/_latestVersion)
```
implementation 'com.chimerapps:mqtt-java:<latest version>
```

Small MQTT library for java

Most of the MQTT 3.1.1 spec has been implemented, with exception of keepalives (which are set to disabled now)

Example usage:
```
    //Create client
    val client = MqttClient.Builder("LeClient")
                .url(HttpUrl.get("http://broker.hivemq.com:8000/mqtt"))
                .connectionType(MqttClient.ConnectionType.WEBSOCKET)
                .protocolVersion(MqttProtocolVersion.VERSION_3_1_1)
                .build()
                
    client.connect(object : MqttClientListener {
                       override fun onConnected(client: MqttClient) {
                       }
           
                       override fun onConnectionFailed(client: MqttClient, result: MqttConnectionResult) {
                       }
           
                       override fun onDisconnected(error: Throwable?) {
                       }
           
                       override fun onMessage(message: MqttMessage) {
                       }
           
                       override fun onActionSuccess(token: MqttToken) {
                       }
           
                       override fun onPong() {
                       }
                   })
                   
    client.publish("/testTopic", "I like turtles".toByteArray(), qos = MqttQoS.EXACTLY_ONCE, isDup = false, retain = false)

```