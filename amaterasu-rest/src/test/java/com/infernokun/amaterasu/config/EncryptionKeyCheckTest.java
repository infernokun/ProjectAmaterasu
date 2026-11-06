package com.infernokun.amaterasu.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptionKeyCheckTest {

    @Test
    void refusesMissingShortOrDefaultKeys() {
        for (String key : new String[]{null, "", "short-key", "secret_key", "CHANGE-ME"}) {
            assertThatThrownBy(() -> EncryptionKeyCheck.requireStrongKey(key)).isInstanceOf(IllegalStateException.class);
        }
        assertThatCode(() -> EncryptionKeyCheck.requireStrongKey("q3Zr8vC1mP0xT6yB2nL5sK9wD4hF7jA0eG3uI8oR1t="))
                .doesNotThrowAnyException();
    }
}
