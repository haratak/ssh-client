package com.harataku.sshclient.tmux

data class TmuxSessionInfo(
    val id: String,
    val name: String,
    val windows: Int,
    val attached: Boolean,
    val cwd: String = "",
    val currentCommand: String = "",
    val gitBranch: String = ""
)
