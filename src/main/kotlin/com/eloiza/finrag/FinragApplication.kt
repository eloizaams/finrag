package com.eloiza.finrag

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class FinragApplication

fun main(args: Array<String>) {
    runApplication<FinragApplication>(*args)
}
