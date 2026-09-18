package cr.acusab.una.sigac.logic.security;

import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
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
@EnableMethodSecurity
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

        http
                .csrf(csrf -> csrf.disable())

                .cors(cors -> cors.configurationSource(request -> {

                    CorsConfiguration configuration =
                            new CorsConfiguration();

                    List<String> allowedOrigins =
                            new ArrayList<>(
                                    Arrays.asList(
                                            "http://localhost:5173",
                                            "https://sigac.acusab.ed.cr"
                                    )
                            );

                    if (extraAllowedOrigins != null
                            && !extraAllowedOrigins.isBlank()) {

                        Arrays.stream(
                                        extraAllowedOrigins.split(",")
                                )
                                .map(String::trim)
                                .filter(origin ->
                                        !origin.isEmpty()
                                                && !allowedOrigins.contains(origin)
                                )
                                .forEach(allowedOrigins::add);
                    }

                    configuration.setAllowedOrigins(
                            allowedOrigins
                    );

                    configuration.setAllowedMethods(
                            Arrays.asList(
                                    "GET",
                                    "POST",
                                    "PUT",
                                    "PATCH",
                                    "DELETE",
                                    "OPTIONS"
                            )
                    );

                    configuration.setAllowedHeaders(
                            Arrays.asList(
                                    "Authorization",
                                    "Content-Type"
                            )
                    );

                    return configuration;
                }))

                .sessionManagement(sessionManagement ->
                        sessionManagement.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(auth -> auth

                        /*
                         * =====================================================
                         * Endpoints públicos
                         * =====================================================
                         */
                        .requestMatchers(
                                "/auth/login",
                                "/auth/logout",
                                "/usuarios",
                                "/roles",
                                "/estados-usuarios"
                        )
                        .permitAll()

                        /*
                         * =====================================================
                         * Gestión de cursos
                         * Rol: DIRECTOR_ACADEMICO
                         * =====================================================
                         */
                        .requestMatchers(
                                HttpMethod.POST,
                                "/cursos"
                        )
                        .hasRole("DIRECTOR_ACADEMICO")

                        /*
                         * Gestión de períodos académicos.
                         * Cambios incorporados desde origin/main.
                         */
                        .requestMatchers(
                                HttpMethod.POST,
                                "/periodos-academicos"
                        )
                        .hasRole("DIRECTOR_ACADEMICO")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/periodos-academicos/todos"
                        )
                        .hasRole("DIRECTOR_ACADEMICO")

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/periodos-academicos/*"
                        )
                        .hasRole("DIRECTOR_ACADEMICO")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/cursos"
                        )
                        .hasRole("DIRECTOR_ACADEMICO")

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/cursos/*/desactivar"
                        )
                        .hasRole("DIRECTOR_ACADEMICO")

                        /*
                         * =====================================================
                         * Gestión de encargados
                         * Rol: ADMINISTRATIVO
                         * =====================================================
                         */
                        .requestMatchers(
                                HttpMethod.POST,
                                "/encargados"
                        )
                        .hasRole("ADMINISTRATIVO")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/encargados/**"
                        )
                        .hasRole("ADMINISTRATIVO")

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/encargados/**"
                        )
                        .hasRole("ADMINISTRATIVO")

                        /*
                         * =====================================================
                         * HU-012 - Confirmar matrícula
                         * Rol: ADMINISTRATIVO
                         *
                         * Permite:
                         * - consultar solicitudes pendientes;
                         * - consultar historial;
                         * - aprobar;
                         * - rechazar.
                         * =====================================================
                         */
                        .requestMatchers(
                                HttpMethod.GET,
                                "/autorizaciones-matricula/**"
                        )
                        .hasRole("ADMINISTRATIVO")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/autorizaciones-matricula"
                        )
                        .hasRole("ADMINISTRATIVO")

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/autorizaciones-matricula/**"
                        )
                        .hasRole("ADMINISTRATIVO")

                        /*
                         * =====================================================
                         * HU-012 - Notificaciones del estudiante
                         * Rol: ESTUDIANTE
                         *
                         * Permite:
                         * - consultar sus propias notificaciones;
                         * - marcar una notificación como leída.
                         * =====================================================
                         */
                        .requestMatchers(
                                HttpMethod.GET,
                                "/notificaciones-estudiante/**"
                        )
                        .hasRole("ESTUDIANTE")

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/notificaciones-estudiante/**"
                        )
                        .hasRole("ESTUDIANTE")

                        /*
                         * Cualquier otro endpoint necesita
                         * un usuario autenticado.
                         */
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