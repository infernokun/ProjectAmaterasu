package com.infernokun.amaterasu.utils;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Slf4j
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rsa")
public class RSAKeyProperties {

    private String privateKey;
    private String publicKey;

    private RSAPrivateKey cachedPrivateKey;
    private RSAPublicKey cachedPublicKey;

    public RSAKeyProperties() {}

    public RSAKeyProperties(String privateKey, String publicKey) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    public RSAPrivateKey getPrivateKey() {
        if (cachedPrivateKey == null) {
            loadKeys();
        }
        return cachedPrivateKey;
    }

    public RSAPublicKey getPublicKey() {
        if (cachedPublicKey == null) {
            loadKeys();
        }
        return cachedPublicKey;
    }

    private synchronized void loadKeys() {
        if (cachedPrivateKey != null && cachedPublicKey != null) {
            return;
        }
        requirePath(privateKey, "rsa.private-key");
        requirePath(publicKey, "rsa.public-key");

        try {
            if (shouldGenerate()) {
                generateKeyFiles();
            }
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            byte[] privateBytes = decodePem(loadKeyContent(privateKey), "PRIVATE KEY");
            byte[] publicBytes = decodePem(loadKeyContent(publicKey), "PUBLIC KEY");
            cachedPrivateKey = (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateBytes));
            cachedPublicKey = (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(publicBytes));
            log.info("Loaded RSA key pair");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load RSA keys from " + privateKey + " and " + publicKey, e);
        }
    }

    private static void requirePath(String path, String property) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException(property + " must be set");
        }
    }

    // Only generate when neither key exists, so a half-present pair is never overwritten.
    private boolean shouldGenerate() {
        return !privateKey.startsWith("classpath:") && !publicKey.startsWith("classpath:")
                && !exists(privateKey) && !exists(publicKey);
    }

    private boolean exists(String keyPath) {
        String path = stripFilePrefix(keyPath);
        return Files.exists(Paths.get(path)) || new ClassPathResource(path).exists();
    }

    private void generateKeyFiles() throws IOException {
        KeyPair pair = KeyGeneratorUtility.generateRsaKey();
        writePem(Paths.get(stripFilePrefix(privateKey)), "PRIVATE KEY", pair.getPrivate().getEncoded());
        writePem(Paths.get(stripFilePrefix(publicKey)), "PUBLIC KEY", pair.getPublic().getEncoded());
        log.warn("No RSA keys found, generated a new pair at {} and {}", privateKey, publicKey);
    }

    private static void writePem(Path path, String type, byte[] der) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        Files.writeString(path, "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n");
    }

    private static byte[] decodePem(String content, String type) {
        String cleaned = content
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replace("-----BEGIN RSA " + type + "-----", "")
                .replace("-----END RSA " + type + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(cleaned);
    }

    private static String stripFilePrefix(String keyPath) {
        return keyPath.startsWith("file:") ? keyPath.substring("file:".length()) : keyPath;
    }

    private String loadKeyContent(String keyPath) throws IOException {
        if (keyPath.startsWith("file:")) {
            return loadFromFileSystem(stripFilePrefix(keyPath));
        }
        if (keyPath.startsWith("classpath:")) {
            return loadFromClasspath(keyPath.substring("classpath:".length()));
        }
        Path path = Paths.get(keyPath);
        if (Files.exists(path)) {
            return Files.readString(path, StandardCharsets.UTF_8);
        }
        return loadFromClasspath(keyPath);
    }

    private static String loadFromClasspath(String resourcePath) throws IOException {
        String path = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new FileNotFoundException("Key not found on filesystem or classpath: " + path);
        }
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String loadFromFileSystem(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new FileNotFoundException("File not found: " + filePath);
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
