package sn.ucak.tpapirest.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import sn.ucak.tpapirest.dto.auth.LoginRequest;
import sn.ucak.tpapirest.dto.auth.TokenResponse;
import sn.ucak.tpapirest.security.AccountUserDetails;
import sn.ucak.tpapirest.security.JwtService;

/** Connexion : vérification des identifiants puis émission du JWT. */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    /**
     * L'email est déjà normalisé ; le mot de passe est comparé tel quel au hachage BCrypt.
     * En cas d'échec, une {@code BadCredentialsException} est levée (traduite en 401 générique).
     */
    public TokenResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
        AccountUserDetails account = (AccountUserDetails) authentication.getPrincipal();
        String token = jwtService.generateToken(account.getId());
        return TokenResponse.bearer(token, jwtService.ttlSeconds());
    }
}
