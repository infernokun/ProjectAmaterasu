package com.infernokun.amaterasu.config;

import com.infernokun.amaterasu.repositories.UserRepository;
import com.infernokun.amaterasu.utils.RSAKeyProperties;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;

import static com.infernokun.amaterasu.models.enums.Role.ADMIN;
import static com.infernokun.amaterasu.models.enums.Role.CREATOR;
import static com.infernokun.amaterasu.models.enums.Role.FACILITATOR;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    public static final String TOKEN_ISSUER = "amaterasu";

    private final RSAKeyProperties keys;
    private final UserRepository userRepository;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        String admin = ADMIN.name();
        String[] authors = {ADMIN.name(), CREATOR.name()};
        String[] runners = {ADMIN.name(), FACILITATOR.name()};
        String[] staff = {ADMIN.name(), CREATOR.name(), FACILITATOR.name()};

        // Bearer tokens only, no cookies or sessions, so CSRF protection does not apply
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> { })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register", "/api/auth/refresh",
                                "/api/auth/refresh/validate", "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/application-info").permitAll()
                        // First-run setup; the controller requires ADMIN once the app is initialized
                        .requestMatchers(HttpMethod.POST, "/api/application-info").permitAll()
                        .requestMatchers("/", "/health", "/error", "/actuator/health/**",
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()

                        // Imports, bulk moves, server edits, moving labs, room staff, lab drafts and the
                        // classification banner. Kept ahead of the broader rules below.
                        .requestMatchers(HttpMethod.POST, "/api/user/import").hasRole(admin)
                        .requestMatchers(HttpMethod.PUT, "/api/user/team/bulk").hasRole(admin)
                        .requestMatchers(HttpMethod.GET, "/api/remote-server/*/settings").hasRole(admin)
                        .requestMatchers(HttpMethod.POST, "/api/remote-server/*/validate").hasRole(admin)
                        .requestMatchers(HttpMethod.PUT, "/api/remote-server/*", "/api/remote-server/*/new-labs").hasRole(admin)
                        .requestMatchers(HttpMethod.POST, "/api/lab-tracker/*/move").hasRole(admin)
                        .requestMatchers(HttpMethod.PUT, "/api/application-info/classification").hasRole(admin)
                        .requestMatchers(HttpMethod.GET, "/api/room/facilitated").hasAnyRole(admin, FACILITATOR.name())
                        .requestMatchers(HttpMethod.GET, "/api/room/facilitator-candidates", "/api/room/*/facilitators")
                                .hasAnyRole(authors)
                        // Who runs a lab hands out lifecycle powers, so only admins pick facilitators
                        .requestMatchers(HttpMethod.PUT, "/api/room/*/facilitators").hasRole(admin)
                        .requestMatchers(HttpMethod.PUT, "/api/room/*/order").hasAnyRole(authors)
                        // A lab's teams and its management: admins and the lab's facilitators (CurrentUser.canRun)
                        .requestMatchers("/api/room/*/teams", "/api/room/*/teams/**", "/api/room/*/manage/**")
                                .hasAnyRole(runners)
                        .requestMatchers(HttpMethod.PUT, "/api/labs/*/publish", "/api/labs/*/unpublish").hasAnyRole(authors)
                        // The controller filters it to what the caller may see
                        .requestMatchers(HttpMethod.GET, "/api/ctf-entity/by-lab/*").authenticated()

                        // Environment lifecycle: admins, and facilitators for the labs they run (checked in
                        // CurrentUser). Removing copies and catalog-wide clean up stay with admins.
                        .requestMatchers(HttpMethod.POST, "/api/labs/start", "/api/labs/pause", "/api/labs/stop")
                                .hasAnyRole(runners)
                        .requestMatchers(HttpMethod.POST, "/api/labs/delete", "/api/labs/delete-lab-from-team").hasRole(admin)
                        .requestMatchers(HttpMethod.POST, "/api/room/*/environment/**").hasAnyRole(runners)
                        .requestMatchers(HttpMethod.POST, "/api/lab-tracker/*/restart/*").hasAnyRole(runners)
                        .requestMatchers("/api/lab-tracker/settings/**", "/api/lab-tracker/logs/**").hasAnyRole(runners)

                        // Teams are run by admins; everyone may ask which team they are on
                        .requestMatchers(HttpMethod.GET, "/api/team/mine").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/team", "/api/team/**").hasAnyRole(runners)

                        // Servers are for the people who build and run labs
                        .requestMatchers(HttpMethod.GET, "/api/remote-server", "/api/remote-server/**",
                                "/api/remote-server-stats", "/api/remote-server-stats/**").hasAnyRole(staff)

                        // Administration
                        .requestMatchers("/actuator/**").hasRole(admin)
                        .requestMatchers(HttpMethod.GET, "/api/audit").hasRole(admin)
                        .requestMatchers(HttpMethod.GET, "/api/user").hasRole(admin)
                        .requestMatchers(HttpMethod.POST, "/api/user", "/api/user/many").hasRole(admin)
                        .requestMatchers(HttpMethod.PUT, "/api/user/**").hasRole(admin)
                        .requestMatchers(HttpMethod.DELETE, "/api/user/**").hasRole(admin)
                        .requestMatchers(HttpMethod.POST, "/api/team", "/api/team/many").hasRole(admin)
                        .requestMatchers(HttpMethod.PUT, "/api/team/**").hasRole(admin)
                        .requestMatchers(HttpMethod.DELETE, "/api/team/**").hasRole(admin)
                        .requestMatchers(HttpMethod.POST, "/api/remote-server", "/api/remote-server/validate").hasRole(admin)
                        .requestMatchers(HttpMethod.POST, "/api/remote-server/*/check").hasRole(admin)
                        .requestMatchers(HttpMethod.DELETE, "/api/remote-server/**").hasRole(admin)
                        .requestMatchers(HttpMethod.POST, "/api/remote-server-stats").hasRole(admin)
                        .requestMatchers(HttpMethod.PUT, "/api/remote-server-stats/**").hasRole(admin)
                        .requestMatchers(HttpMethod.DELETE, "/api/remote-server-stats/**").hasRole(admin)
                        .requestMatchers(HttpMethod.POST, "/api/lab-tracker", "/api/lab-tracker/many").hasRole(admin)
                        .requestMatchers(HttpMethod.PUT, "/api/lab-tracker/**").hasRole(admin)
                        .requestMatchers(HttpMethod.DELETE, "/api/lab-tracker/**").hasRole(admin)
                        .requestMatchers("/api/labs/dev", "/api/labs/delete-item/**").hasRole(admin)

                        // Running an event: every team's labs and the live room view
                        .requestMatchers(HttpMethod.GET, "/api/lab-tracker/overview", "/api/room/*/live")
                                .hasAnyRole(admin, FACILITATOR.name())

                        // Authoring labs, rooms and challenges
                        .requestMatchers(HttpMethod.POST, "/api/labs", "/api/labs/many", "/api/labs/upload/**",
                                "/api/labs/validate").hasAnyRole(authors)
                        .requestMatchers(HttpMethod.PUT, "/api/labs/**").hasAnyRole(authors)
                        .requestMatchers(HttpMethod.DELETE, "/api/labs/*").hasAnyRole(authors)
                        .requestMatchers(HttpMethod.GET, "/api/labs/check/**", "/api/labs/settings/**").hasAnyRole(authors)
                        .requestMatchers("/api/proxmox/**").hasAnyRole(authors)
                        .requestMatchers(HttpMethod.POST, "/api/room", "/api/room/many", "/api/room/*/files").hasAnyRole(authors)
                        .requestMatchers(HttpMethod.PUT, "/api/room/**").hasAnyRole(authors)
                        .requestMatchers(HttpMethod.DELETE, "/api/room/**").hasAnyRole(authors)
                        .requestMatchers(HttpMethod.POST, "/api/room/add-points/**").hasAnyRole(admin, FACILITATOR.name())
                        .requestMatchers(HttpMethod.POST, "/api/ctf-entity", "/api/ctf-entity/batch").hasAnyRole(authors)
                        .requestMatchers(HttpMethod.PUT, "/api/ctf-entity/**").hasAnyRole(authors)
                        .requestMatchers(HttpMethod.DELETE, "/api/ctf-entity/**").hasAnyRole(authors)
                        // Raw challenge lists are for authors; players get questions from the lab overview
                        .requestMatchers(HttpMethod.GET, "/api/ctf-entity/**", "/api/ctf-entity").hasAnyRole(authors)

                        // The caller's own sessions
                        .requestMatchers("/api/auth/sessions", "/api/auth/sessions/**").authenticated()

                        // Top bar search, notifications and VM consoles: any signed in user. Search results
                        // are scoped by role, notifications to their recipient, and a console to whoever
                        // works in that copy (or runs it), in the services and the console relay.
                        .requestMatchers(HttpMethod.GET, "/api/search").authenticated()
                        .requestMatchers("/api/notifications", "/api/notifications/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/lab-tracker/*/vms/*/console").authenticated()
                        .requestMatchers("/socket-handler/console/**").authenticated()

                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(bearerTokenResolver())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint((request, response, e) ->
                                writeError(response, HttpStatus.UNAUTHORIZED, "Authentication required"))
                        .accessDeniedHandler((request, response, e) ->
                                writeError(response, HttpStatus.FORBIDDEN, "Access denied")))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, e) ->
                                writeError(response, HttpStatus.UNAUTHORIZED, "Authentication required"))
                        .accessDeniedHandler((request, response, e) ->
                                writeError(response, HttpStatus.FORBIDDEN, "Access denied")));

        return http.build();
    }

    // Browsers can't set headers on a websocket handshake, so the socket passes the token as access_token
    @Bean
    public BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver headerOnly = new DefaultBearerTokenResolver();
        DefaultBearerTokenResolver headerOrQuery = new DefaultBearerTokenResolver();
        headerOrQuery.setAllowUriQueryParameter(true);
        return request -> request.getRequestURI().startsWith(request.getContextPath() + "/socket-handler/")
                ? headerOrQuery.resolve(request) : headerOnly.resolve(request);
    }

    private static void writeError(jakarta.servlet.http.HttpServletResponse response, HttpStatus status,
                                   String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":" + status.value() + ",\"message\":\"" + message + "\",\"data\":null}");
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService) {
        DaoAuthenticationProvider daoAuthenticationProvider = new DaoAuthenticationProvider(userDetailsService);
        daoAuthenticationProvider.setPasswordEncoder(this.passwordEncoder());
        return new ProviderManager(daoAuthenticationProvider);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keys.getPublicKey()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(TOKEN_ISSUER),
                new ActiveUserTokenValidator(userRepository)));
        return decoder;
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        JWK jwk = new RSAKey.Builder(keys.getPublicKey()).privateKey(keys.getPrivateKey()).build();
        JWKSource<SecurityContext> jwks = new ImmutableJWKSet<>(new JWKSet(jwk));
        return new NimbusJwtEncoder(jwks);
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
}
