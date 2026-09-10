package cn.ningbingjian.learnjava.security.lesson008;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordStorageTests {
    private final UserConfiguration configuration = new UserConfiguration();
    private final String raw = "storage-test-only";

    @Test
    void storedUsersHaveEncodedPasswordsAndDistinctRoleMetadata() {
        var encoder = configuration.passwordEncoder();
        var users = configuration.userDetailsService(encoder, raw, raw, raw);
        assertThat(encoder).isInstanceOf(DelegatingPasswordEncoder.class);
        for (String name : new String[]{"member", "support", "admin"}) {
            var user = users.loadUserByUsername(name);
            assertThat(user.getPassword()).startsWith("{bcrypt}").isNotEqualTo(raw);
            assertThat(encoder.matches(raw, user.getPassword())).isTrue();
            assertThat(encoder.matches("wrong", user.getPassword())).isFalse();
            assertThat(user.getAuthorities()).extracting("authority").containsExactly("ROLE_" + name.toUpperCase(java.util.Locale.ROOT));
        }
        assertThatThrownBy(() -> users.loadUserByUsername("user")).isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void sameRawValueHasDifferentSaltedEncodingsButBothMatch() {
        var encoder = configuration.passwordEncoder();
        String first = encoder.encode(raw);
        String second = encoder.encode(raw);
        assertThat(first).isNotEqualTo(second);
        assertThat(encoder.matches(raw, first)).isTrue();
        assertThat(encoder.matches(raw, second)).isTrue();
    }

    @Test
    void missingAlgorithmIdFailsAndRestoringTheFullRecordRepairsIt() {
        var encoder = configuration.passwordEncoder();
        String complete = encoder.encode(raw);
        String broken = complete.substring("{bcrypt}".length());
        assertThatThrownBy(() -> encoder.matches(raw, broken)).isInstanceOf(IllegalArgumentException.class);
        assertThat(encoder.matches(raw, complete)).isTrue();
    }

    @Test
    void encodingAnEncodedRecordIsNotAValidPasswordMigration() {
        var encoder = configuration.passwordEncoder();
        // 用短的既有编码表示演示二次编码，避免混入bcrypt的72字节限制。
        String stored = "{noop}" + raw;
        String doubleEncoded = encoder.encode(stored);
        assertThat(encoder.matches(raw, doubleEncoded)).isFalse();
        assertThat(encoder.matches(raw, encoder.encode(raw))).isTrue();
    }

    @Test
    void missingBlankAndOversizedExperimentPasswordsAreRejectedWithoutEchoingInput() {
        var encoder = configuration.passwordEncoder();
        for (String value : new String[]{"", "        ", "short", "a".repeat(73), "汉".repeat(25)}) {
            assertThatThrownBy(() -> configuration.userDetailsService(encoder, value, raw, raw))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("环境变量");
        }
    }
}
