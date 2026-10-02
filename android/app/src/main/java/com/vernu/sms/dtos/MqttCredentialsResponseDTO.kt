package com.vernu.sms.dtos

class MqttCredentialsResponseDTO {
    @JvmField var data: MqttCredentialsDTO? = null
}

class MqttCredentialsDTO {
    @JvmField var url: String? = null
    @JvmField var username: String? = null
    @JvmField var password: String? = null
    @JvmField var topicPrefix: String? = null
}
