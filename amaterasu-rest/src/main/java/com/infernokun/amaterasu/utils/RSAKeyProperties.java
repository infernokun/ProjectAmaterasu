package com.infernokun.amaterasu.utils;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@ConfigurationProperties(prefix = "rsa")
@Component
@Getter
@Setter
public class RSAKeyProperties {

    private String privateKey;
    private String publicKey;

    public RSAKeyProperties() {}

    public RSAKeyProperties(String privateKey, String publicKey) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    // Method to get RSAPrivateKey from file
    public RSAPrivateKey getPrivateKey() {
        try {
            String privateKeyContent = loadKeyFromFile(privateKey);
            privateKeyContent = privateKeyContent
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] keyBytes = Base64.getDecoder().decode(privateKeyContent);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) keyFactory.generatePrivate(spec);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load private key", e);
        }
    }

    // Method to get RSAPublicKey from file
    public RSAPublicKey getPublicKey() {
        try {
            String publicKeyContent = loadKeyFromFile(publicKey);
            publicKeyContent = publicKeyContent
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] keyBytes = Base64.getDecoder().decode(publicKeyContent);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) keyFactory.generatePublic(spec);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load public key", e);
        }
    }

    private String loadKeyFromFile(String keyPath) throws IOException {
        if (keyPath.startsWith("classpath:")) {
            String resourcePath = keyPath.substring("classpath:".length());
            try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                if (inputStream == null) {
                    throw new FileNotFoundException("Key file not found: " + resourcePath);
                }
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } else {
            return Files.readString(Paths.get(keyPath), StandardCharsets.UTF_8);
        }
    }
}