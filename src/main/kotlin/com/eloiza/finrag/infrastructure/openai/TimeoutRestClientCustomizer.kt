package com.eloiza.finrag.infrastructure.openai

import org.springframework.boot.restclient.RestClientCustomizer
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

@Component
class TimeoutRestClientCustomizer : RestClientCustomizer {
    override fun customize(restClientBuilder: RestClient.Builder) {
        val httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build()
        val requestFactory = JdkClientHttpRequestFactory(httpClient).apply { setReadTimeout(READ_TIMEOUT) }
        restClientBuilder.requestFactory(requestFactory)
    }

    private companion object {
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)
        val READ_TIMEOUT: Duration = Duration.ofSeconds(30)
    }
}
