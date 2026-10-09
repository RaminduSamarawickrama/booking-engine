package com.booking.pricing;

import com.booking.pricing.maps.MapsProvider;
import com.booking.pricing.maps.MockMaps;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MapsConfiguration {

    @Bean
    @ConditionalOnProperty(name = "booking.providers.maps", havingValue = "mock", matchIfMissing = true)
    MapsProvider mockMaps() {
        return new MockMaps();
    }
}
