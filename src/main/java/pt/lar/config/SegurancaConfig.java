package pt.lar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SegurancaConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * /admin/** exige login (ENFERMEIRO ou GERONTOLOGO, mesmas permissões nesta fase).
     * /tablet/**, /fotos/** e ficheiros estáticos são abertos (rede local). CSRF desligado só no tablet.
     */
    @Bean
    SecurityFilterChain filtros(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(a -> a
                .requestMatchers("/admin/login", "/css/**", "/js/**", "/icons/**").permitAll()
                .requestMatchers("/admin/**").hasAnyRole("ENFERMEIRO", "GERONTOLOGO")
                .anyRequest().permitAll())
            .formLogin(f -> f
                .loginPage("/admin/login")
                .loginProcessingUrl("/admin/login")
                .defaultSuccessUrl("/admin/utentes", true)
                .failureUrl("/admin/login?erro"))
            .logout(l -> l
                .logoutUrl("/admin/logout")
                .logoutSuccessUrl("/admin/login?saiu"))
            .csrf(c -> c.ignoringRequestMatchers("/tablet/**"));
        return http.build();
    }
}
