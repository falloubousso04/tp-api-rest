package sn.ucak.tpapirest.security;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import sn.ucak.tpapirest.entity.Role;
import sn.ucak.tpapirest.repository.UserRepository;

/**
 * Configuration Spring Security d'une API sans état.
 *
 * <ul>
 *   <li>Pas de session serveur, pas de formulaire de connexion, pas de HTTP Basic.</li>
 *   <li>CSRF désactivé : le token circule uniquement dans l'en-tête Authorization et aucun
 *       cookie d'authentification n'est utilisé, le navigateur ne peut donc pas l'envoyer
 *       automatiquement à l'insu de l'utilisateur.</li>
 *   <li>Seules les ressources de documentation, l'inscription et la connexion sont publiques.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Ressources de Swagger UI et du document OpenAPI, accessibles sans token. */
    static final String[] DOCUMENTATION_PATHS = {
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml"
    };

    private static final String ADMIN = Role.ADMIN.name();
    private static final String USER = Role.USER.name();

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtService jwtService,
                                            UserRepository userRepository,
                                            JsonAuthenticationEntryPoint authenticationEntryPoint,
                                            JsonAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(DOCUMENTATION_PATHS).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**").hasAnyRole(USER, ADMIN)
                        .requestMatchers("/api/products", "/api/products/**").hasRole(ADMIN)
                        .requestMatchers("/api/users", "/api/users/**").hasRole(ADMIN)
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, userRepository),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Recherche du compte par email normalisé, utilisée uniquement à la connexion. */
    @Bean
    UserDetailsService userDetailsService(UserRepository userRepository) {
        return email -> userRepository.findByEmail(email)
                .map(AccountUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Compte introuvable"));
    }

    /**
     * Vérifie email et mot de passe. Un email inconnu et un mauvais mot de passe produisent la même
     * {@code BadCredentialsException}, et un hachage factice est calculé pour un email inconnu afin
     * que la durée de réponse ne révèle pas l'existence du compte.
     */
    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
