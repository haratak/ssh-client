package com.harataku.sshclient.ssh

data class SshConnectionConfig(
    val host: String = "",
    val port: Int = 22,
    val username: String = "",
    val password: String = ""
)
