package com.booking.auth.web;

import java.time.Duration;
import java.util.Map;

import com.booking.auth.config.SigningKey;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public keys other services use to verify access tokens. */
@RestController
public class JwksController {

    private final SigningKey key;

    public JwksController(SigningKey key) {
        this.key = key;
    }

    @GetMapping("/.well-known/jwks.json")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(key.publicJwkSet());
    }
}
