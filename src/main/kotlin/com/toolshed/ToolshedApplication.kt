package com.toolshed

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ToolshedApplication

fun main(args: Array<String>) {
    runApplication<ToolshedApplication>(*args)
}
