package com.booking.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

import com.booking.auth.keys.SigningKey;

import org.junit.jupiter.api.Test;

class SigningKeyTest {

    private static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(der) + "\n-----END " + type + "-----\n";
    }

    @Test
    void loadsTheOpensslKeyPairFormat() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = generator.generateKeyPair();
        String both = pem("PRIVATE KEY", pair.getPrivate().getEncoded()) + pem("PUBLIC KEY", pair.getPublic().getEncoded());

        SigningKey key = SigningKey.fromBase64Pem(Base64.getEncoder().encodeToString(both.getBytes(StandardCharsets.US_ASCII)));

        assertThat(key.keyId()).isNotBlank();
        assertThat(key.publicJwkSet().toString()).doesNotContain("\"d\"").doesNotContain("d=");
    }

    @Test
    void rejectsAPrivateKeyWithoutItsPublicHalf() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        String only = pem("PRIVATE KEY", generator.generateKeyPair().getPrivate().getEncoded());

        assertThatThrownBy(() -> SigningKey.fromBase64Pem(
                Base64.getEncoder().encodeToString(only.getBytes(StandardCharsets.US_ASCII))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PUBLIC KEY");
    }

    @Test
    void generatedKeysAreUniquePerStart() {
        assertThat(SigningKey.generate().keyId()).isNotEqualTo(SigningKey.generate().keyId());
    }
}
