package com.k.selena.system

interface SystemActions {
    fun openApp(query: String): Boolean
    fun openTermuxWithCommand(command: String?): Boolean
}
