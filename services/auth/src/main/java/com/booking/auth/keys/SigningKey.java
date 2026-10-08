package com.booking.auth.keys;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;

/**
 * The P-256 key auth-service signs access tokens with. Configured as base64 of two PEM
 * blocks, the PKCS#8 private key followed by its public key, exactly as produced by:
 *
 * <pre>
 * openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out signing.pem
 * cat signing.pem &lt;(openssl pkey -in signing.pem -pubout) | base64 -w0
 * </pre>
 */
public final class SigningKey {

    private static final Pattern PEM = Pattern.compile(
            "-----BEGIN ([A-Z ]+)-----([A-Za-z0-9+/=\\s]+)-----END \\1-----");

    private final ECKey jwk;

    private SigningKey(ECPublicKey publicKey, ECPrivateKey privateKey) {
        try {
            this.jwk = new ECKey.Builder(Curve.P_256, publicKey)
                    .privateKey(privateKey)
                    .keyUse(KeyUse.SIGNATURE)
                    .keyIDFromThumbprint()
                    .build();
        } catch (JOSEException e) {
            throw new IllegalStateException("Cannot build signing key", e);
        }
    }

    public static SigningKey fromBase64Pem(String base64) {
        String pem = new String(Base64.getMimeDecoder().decode(base64.trim()), StandardCharsets.US_ASCII);
        byte[] privateDer = null;
        byte[] publicDer = null;
        Matcher m = PEM.matcher(pem);
        while (m.find()) {
            byte[] der = Base64.getMimeDecoder().decode(m.group(2).replaceAll("\\s", ""));
            switch (m.group(1)) {
                case "PRIVATE KEY" -> privateDer = der;
                case "PUBLIC KEY" -> publicDer = der;
                default -> throw new IllegalArgumentException(
                        "Unexpected PEM block '" + m.group(1) + "'; expected PKCS#8 PRIVATE KEY and PUBLIC KEY");
            }
        }
        if (privateDer == null || publicDer == null) {
            throw new IllegalArgumentException(
                    "AUTH_JWT_PRIVATE_KEY_B64 must hold a PRIVATE KEY and a PUBLIC KEY PEM block");
        }
        try {
            KeyFactory ec = KeyFactory.getInstance("EC");
            ECPrivateKey privateKey = (ECPrivateKey) ec.generatePrivate(new PKCS8EncodedKeySpec(privateDer));
            ECPublicKey publicKey = (ECPublicKey) ec.generatePublic(new X509EncodedKeySpec(publicDer));
            if (privateKey.getParams().getCurve().getField().getFieldSize() != 256) {
                throw new IllegalArgumentException("The signing key must be on curve P-256");
            }
            return new SigningKey(publicKey, privateKey);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalArgumentException("AUTH_JWT_PRIVATE_KEY_B64 is not a valid EC key pair", e);
        }
    }

    public static SigningKey generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair pair = generator.generateKeyPair();
            return new SigningKey((ECPublicKey) pair.getPublic(), (ECPrivateKey) pair.getPrivate());
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Cannot generate a P-256 key", e);
        }
    }

    public ECKey jwk() {
        return jwk;
    }

    public String keyId() {
        return jwk.getKeyID();
    }

    /** The public JWK set served at /.well-known/jwks.json. Never contains the private part. */
    public Map<String, Object> publicJwkSet() {
        return new JWKSet(jwk.toPublicJWK()).toJSONObject(true);
    }
}
