package com.gc.waravi.skyway.popservice.models

data class MailServerInfo(val host: String, val user: String, val password: String, val port: Int, val encryption: EncryptionType)

enum class EncryptionType {
    None,
    Ssl,
    Tls
}