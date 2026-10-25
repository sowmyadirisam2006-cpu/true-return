
package com.truereturn.config;

import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(
                    CookieCsrfTokenRepository.withHttpOnlyFalse()
                )
                .csrfTokenRequestHandler(
                    new SpaCsrfTokenRequestHandler()
                )
                .ignoringRequestMatchers(
                "/api/auth/login",
                "/api/auth/register",
                "/api/hardware/data"
                )
            )

            .securityContext(context -> context
                .securityContextRepository(
                    new HttpSessionSecurityContextRepository()
                )
            )

            .authorizeHttpRequests(auth -> auth

                // Public frontend resources
                .requestMatchers(
                    "/",
                    "/index.html",
                    "/css/**",
                    "/js/**",
                    "/favicon.ico"
                ).permitAll()

                // Public authentication endpoints
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/auth/login",
                    "/api/auth/register"
                ).permitAll()

                // Logout requires an authenticated session
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/auth/logout"
                ).authenticated()

                // Public product verification endpoints
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/products",
                    "/api/products/",
                    "/api/products/test",
                    "/api/products/verify/**"
                ).permitAll()
                // ESP32 hardware endpoints
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/hardware/data"
                ).permitAll()

                .requestMatchers(
                    HttpMethod.GET,
                    "/api/hardware/latest"
                ).permitAll()

                // Protect everything else
                .anyRequest().authenticated()
            )

            .sessionManagement(session -> session
                .sessionCreationPolicy(
                    SessionCreationPolicy.IF_REQUIRED
                )
            );

        return http.build();
    }

    private static final class SpaCsrfTokenRequestHandler
            implements CsrfTokenRequestHandler {

        private final CsrfTokenRequestAttributeHandler plainHandler =
                new CsrfTokenRequestAttributeHandler();

        private final XorCsrfTokenRequestAttributeHandler xorHandler =
                new XorCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(
                HttpServletRequest request,
                HttpServletResponse response,
                Supplier<CsrfToken> csrfToken) {

            xorHandler.handle(request, response, csrfToken);

            // Ensure the CSRF cookie can be created.
            csrfToken.get();
        }

        @Override
        public String resolveCsrfTokenValue(
                HttpServletRequest request,
                CsrfToken csrfToken) {

            String headerValue =
                    request.getHeader(csrfToken.getHeaderName());

            if (headerValue != null && !headerValue.isBlank()) {
                return plainHandler.resolveCsrfTokenValue(
                        request, csrfToken
                );
            }

            return xorHandler.resolveCsrfTokenValue(
                    request, csrfToken
            );
        }
    }
}
