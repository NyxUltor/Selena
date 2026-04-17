package com.k.selena.system

interface RootCommandExecutor {
    fun execute(command: String): Int
}
