package cn.ningbingjian.learnjava.security.lesson006;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Configuration(proxyBeanMethods = false)
public class UserConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(PasswordEncoder encoder,
            @Value("${lesson.users.member-password}") String memberPassword,
            @Value("${lesson.users.support-password}") String supportPassword,
            @Value("${lesson.users.admin-password}") String adminPassword) {
        return new InMemoryUserDetailsManager(
                User.withUsername("member").password(encode(encoder, memberPassword)).roles("MEMBER").build(),
                User.withUsername("support").password(encode(encoder, supportPassword)).roles("SUPPORT").build(),
                User.withUsername("admin").password(encode(encoder, adminPassword)).roles("ADMIN").build());
    }

    private static String encode(PasswordEncoder encoder, String raw) {
        // 实验输入约束；bcrypt按UTF-8字节计限，不能只检查字符数。
        if (raw == null || raw.isBlank() || raw.length() < 8
                || raw.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("实验密码须至少8个字符且UTF-8不超过72字节，请设置三个LESSON_*_PASSWORD环境变量。");
        }
        return encoder.encode(raw);
    }
}
