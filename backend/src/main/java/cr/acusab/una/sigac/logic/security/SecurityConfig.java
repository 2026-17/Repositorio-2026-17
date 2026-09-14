package cr.acusab.una.sigac.logic.security;

import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@AllArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${cors.allowed-origins:}") String extraAllowedOrigins
    ) throws Exception {

        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(request -> {

                    CorsConfiguration configuration = new CorsConfiguration();

                    // Orígenes fijos.
                    List<String> allowedOrigins = new ArrayList<>(Arrays.asList(
                            "http://localhost:5173",
                            "https://api.acusab.ed.cr"
                    ));

                    // Orígenes adicionales configurados mediante
                    // variable de entorno.
                    if (extraAllowedOrigins != null
                            && !extraAllowedOrigins.isBlank()) {

                        Arrays.stream(extraAllowedOrigins.split(","))
                                .map(String::trim)
                                .filter(origin ->
                                        !origin.isEmpty()
                                                && !allowedOrigins.contains(origin)
                                )
                                .forEach(allowedOrigins::add);
                    }

                    configuration.setAllowedOrigins(allowedOrigins);

                    configuration.setAllowedMethods(Arrays.asList(
                            "GET",
                            "POST",
                            "PUT",
                            "PATCH",
                            "DELETE",
                            "OPTIONS"
                    ));

                    configuration.setAllowedHeaders(Arrays.asList(
                            "Authorization",
                            "Content-Type"
                    ));

                    return configuration;
                }))
                .sessionManagement(sessionManagement ->
                        sessionManagement.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )
                .authorizeHttpRequests(auth -> auth

                        // Rutas públicas existentes.
                        .requestMatchers(
                                "/auth/login",
                                "/usuarios",
                                "/roles",
                                "/estados-usuarios"
                        ).permitAll()

                        // HU-028 - Crear curso en la malla curricular.
                        // Solo el Director Académico puede crear cursos.
                        .requestMatchers(HttpMethod.POST, "/cursos")
                        .hasRole("DIRECTOR_ACADEMICO")

                        // HU-031 - Desactivar curso de la malla curricular.
                        // Solo el Director Académico puede desactivar cursos.
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/cursos/*/desactivar"
                        )
                        .hasRole("DIRECTOR_ACADEMICO")

                        // Las demás rutas requieren autenticación.
                        .anyRequest()
                        .authenticated()
                )
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config
    ) throws Exception {
        return config.getAuthenticationManager();
    }
}