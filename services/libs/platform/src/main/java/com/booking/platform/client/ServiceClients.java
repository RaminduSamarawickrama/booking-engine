package com.booking.platform.client;

import java.time.Duration;

import com.booking.platform.web.RequestIds;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * RestClients for calls between services: short timeouts so one slow service cannot stall
 * another, and the current request id forwarded so logs line up across services.
 */
public final class ServiceClients {

    private ServiceClients() {
    }

    public static RestClient create(String baseUrl) {
        return create(baseUrl, Duration.ofSeconds(2), Duration.ofSeconds(5));
    }

    public static RestClient create(String baseUrl, Duration connectTimeout, Duration responseTimeout) {
        CloseableHttpClient http = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.of(connectTimeout))
                                .setSocketTimeout(Timeout.of(responseTimeout))
                                .build())
                        .setMaxConnPerRoute(20)
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setResponseTimeout(Timeout.of(responseTimeout))
                        .build())
                .disableAutomaticRetries()
                .build();
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(new HttpComponentsClientHttpRequestFactory(http))
                .requestInterceptor((request, body, execution) -> {
                    RequestIds.current().ifPresent(id -> request.getHeaders().set(RequestIds.HEADER, id));
                    return execution.execute(request, body);
                })
                .build();
    }
}
