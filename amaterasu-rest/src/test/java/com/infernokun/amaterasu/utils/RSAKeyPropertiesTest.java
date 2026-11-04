package com.infernokun.amaterasu.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RSAKeyPropertiesTest {

    @TempDir
    Path dir;

    @Test
    void generatesKeysWhenMissingAndReusesThem() {
        Path privateKey = dir.resolve("keys/private.pem");
        Path publicKey = dir.resolve("keys/public.pem");

        RSAKeyProperties first = new RSAKeyProperties(privateKey.toString(), publicKey.toString());
        assertThat(first.getPublicKey()).isNotNull();
        assertThat(Files.exists(privateKey)).isTrue();
        assertThat(Files.exists(publicKey)).isTrue();

        RSAKeyProperties second = new RSAKeyProperties("file:" + privateKey, "file:" + publicKey);
        assertThat(second.getPublicKey()).isEqualTo(first.getPublicKey());
        assertThat(second.getPrivateKey()).isEqualTo(first.getPrivateKey());
    }

    @Test
    void doesNotOverwriteWhenOnlyOneKeyExists() throws Exception {
        Path privateKey = dir.resolve("private.pem");
        Path publicKey = dir.resolve("public.pem");
        Files.writeString(publicKey, "not a key");

        RSAKeyProperties props = new RSAKeyProperties(privateKey.toString(), publicKey.toString());
        assertThatThrownBy(props::getPublicKey).isInstanceOf(IllegalStateException.class);
        assertThat(Files.readString(publicKey)).isEqualTo("not a key");
    }
}
