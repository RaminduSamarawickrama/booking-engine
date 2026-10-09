package com.booking.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.booking.auth.domain.PasswordPolicy;
import com.booking.platform.error.ApiException;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void acceptsLongPassphrases() {
        assertThatCode(() -> PasswordPolicy.check("tea at four by the river")).doesNotThrowAnyException();
    }

    @Test
    void rejectsShortLongAndCommonPasswords() {
        assertThatThrownBy(() -> PasswordPolicy.check("nine char")).isInstanceOf(ApiException.class)
                .hasMessageContaining("at least");
        assertThatThrownBy(() -> PasswordPolicy.check("x".repeat(73))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PasswordPolicy.check("Password123")).isInstanceOf(ApiException.class);
    }
}
