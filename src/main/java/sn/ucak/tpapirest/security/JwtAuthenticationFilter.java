package sn.ucak.tpapirest.security;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import sn.ucak.tpapirest.entity.User;
import sn.ucak.tpapirest.repository.UserRepository;

/**
 * Authentifie la requête à partir de l'en-tête {@code Authorization: Bearer <token>}.
 *
 * <p>Après vérification du token, le compte est relu en base à partir du sujet : un compte
 * supprimé n'est plus authentifié (401) et un changement de rôle s'applique dès la requête
 * suivante. Rien n'est stocké en session.</p>
 *
 * <p>Un token absent ou refusé ne bloque pas directement la requête : elle continue sans
 * authentification, ce qui laisse accessibles les routes publiques. Pour une route protégée,
 * Spring Security renvoie alors 401 via {@link JsonAuthenticationEntryPoint}, avec le motif
 * mémorisé dans {@link #AUTH_ERROR_ATTRIBUTE}. Le token n'est jamais journalisé.</p>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Attribut de requête contenant le motif de refus du token, repris dans la réponse 401. */
    public static final String AUTH_ERROR_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".error";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();
    private final WebAuthenticationDetailsSource detailsSource = new WebAuthenticationDetailsSource();

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        List<String> headers = Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION));
        if (!headers.isEmpty()) {
            authenticate(request, headers);
        }
        chain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, List<String> headers) {
        if (headers.size() > 1) {
            reject(request, "Un seul en-tête Authorization est accepté");
            return;
        }
        String header = headers.getFirst();
        if (!header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            reject(request, "Schéma d'authentification non pris en charge : utilisez Bearer");
            return;
        }
        String token = header.substring(BEARER_PREFIX.length()).strip();
        if (token.isEmpty()) {
            reject(request, InvalidTokenException.Reason.INVALID.message());
            return;
        }

        Long userId;
        try {
            userId = jwtService.extractUserId(token);
        } catch (InvalidTokenException exception) {
            reject(request, exception.reason().message());
            return;
        }

        Optional<User> account = userRepository.findById(userId);
        if (account.isEmpty()) {
            reject(request, "Le compte associé à ce token n'existe plus");
            return;
        }

        User user = account.get();
        AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole());
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority(user.getRole().authority())));
        authentication.setDetails(detailsSource.buildDetails(request));

        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
    }

    private void reject(HttpServletRequest request, String reason) {
        securityContextHolderStrategy.clearContext();
        request.setAttribute(AUTH_ERROR_ATTRIBUTE, reason);
    }
}
