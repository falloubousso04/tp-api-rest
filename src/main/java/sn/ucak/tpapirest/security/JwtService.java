package sn.ucak.tpapirest.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/**
 * Émission et vérification des JWT signés en HS256.
 *
 * <p>Le token contient uniquement l'identifiant de l'utilisateur ({@code sub}), la date
 * d'émission ({@code iat}) et la date d'expiration ({@code exp}) : ni email, ni rôle, ni secret.
 * Le rôle est relu en base à chaque requête.</p>
 */
@Service
public class JwtService {

    static final String ALGORITHM = "HS256";
    static final int MIN_KEY_BYTES = 32;

    private final SecretKey signingKey;
    private final long ttlSeconds;
    private final Clock clock;
    private final JwtParser parser;

    public JwtService(JwtProperties properties, Clock clock) {
        this.signingKey = Keys.hmacShaKeyFor(decodeKey(properties.secretBase64()));
        this.ttlSeconds = properties.ttlSeconds();
        this.clock = clock;
        this.parser = Jwts.parser()
                .verifyWith(signingKey)
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    /** Durée de validité des tokens émis, en secondes. */
    public long ttlSeconds() {
        return ttlSeconds;
    }

    public String generateToken(Long userId) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Vérifie la signature, l'algorithme attendu et l'expiration, puis retourne l'identifiant
     * de l'utilisateur. Les tokens non signés, modifiés ou mal formés sont refusés.
     */
    public Long extractUserId(String token) {
        try {
            // parseSignedClaims refuse les tokens non signés (alg "none") et les signatures invalides.
            Jws<Claims> jws = parser.parseSignedClaims(token);
            if (!ALGORITHM.equals(jws.getHeader().getAlgorithm())) {
                throw new InvalidTokenException(InvalidTokenException.Reason.INVALID);
            }
            Claims claims = jws.getPayload();
            if (claims.getIssuedAt() == null || claims.getExpiration() == null || claims.getSubject() == null) {
                throw new InvalidTokenException(InvalidTokenException.Reason.INVALID);
            }
            return Long.valueOf(claims.getSubject());
        } catch (ExpiredJwtException expired) {
            throw new InvalidTokenException(InvalidTokenException.Reason.EXPIRED);
        } catch (JwtException | IllegalArgumentException invalid) {
            // IllegalArgumentException couvre aussi un sujet non numérique (NumberFormatException).
            throw new InvalidTokenException(InvalidTokenException.Reason.INVALID);
        }
    }

    private static byte[] decodeKey(String secretBase64) {
        byte[] key;
        try {
            key = Decoders.BASE64.decode(secretBase64.strip());
        } catch (DecodingException | IllegalArgumentException exception) {
            throw new IllegalStateException("JWT_SECRET_BASE64 n'est pas une valeur Base64 valide");
        }
        if (key.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("JWT_SECRET_BASE64 doit contenir au moins " + MIN_KEY_BYTES
                    + " octets une fois décodé (" + key.length + " octets reçus). "
                    + "Générez-la avec scripts/generate-jwt-secret.ps1 ou scripts/generate-jwt-secret.sh");
        }
        return key;
    }
}
