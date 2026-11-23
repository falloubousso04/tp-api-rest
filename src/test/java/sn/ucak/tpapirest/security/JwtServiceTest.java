package sn.ucak.tpapirest.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-24T10:00:00Z");
    private static final long TTL = 900;

    private final byte[] keyBytes = randomBytes(32);
    private final String secret = Base64.getEncoder().encodeToString(keyBytes);
    private final JwtService jwtService = service(secret, Clock.fixed(NOW, ZoneOffset.UTC));

    private static JwtService service(String secret, Clock clock) {
        return new JwtService(new JwtProperties(secret, TTL), clock);
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void generatedTokenContainsOnlySubjectIssuedAtAndExpiration() {
        String token = jwtService.generateToken(42L);

        Claims claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(keyBytes))
                .clock(() -> Date.from(NOW)).build().parseSignedClaims(token).getPayload();
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.getIssuedAt().toInstant()).isEqualTo(NOW);
        assertThat(claims.getExpiration().toInstant()).isEqualTo(NOW.plusSeconds(TTL));
        assertThat(claims.keySet()).containsExactlyInAnyOrder("sub", "iat", "exp");
        assertThat(jwtService.ttlSeconds()).isEqualTo(900);
    }

    @Test
    void tokenUsesHs256() {
        String header = new String(Base64.getUrlDecoder().decode(jwtService.generateToken(1L).split("\\.")[0]),
                StandardCharsets.UTF_8);
        assertThat(header).contains("\"alg\":\"HS256\"");
    }

    @Test
    void extractsUserIdFromValidToken() {
        assertThat(jwtService.extractUserId(jwtService.generateToken(7L))).isEqualTo(7L);
    }

    @Test
    void rejectsExpiredToken() {
        String token = jwtService.generateToken(7L);
        JwtService later = service(secret, Clock.fixed(NOW.plus(Duration.ofSeconds(TTL + 1)), ZoneOffset.UTC));

        assertThatThrownBy(() -> later.extractUserId(token))
                .isInstanceOfSatisfying(InvalidTokenException.class,
                        e -> assertThat(e.reason()).isEqualTo(InvalidTokenException.Reason.EXPIRED));
    }

    @Test
    void rejectsTamperedPayload() {
        String[] parts = jwtService.generateToken(7L).split("\\.");
        String forged = parts[0] + "." + base64Url("{\"sub\":\"1\",\"iat\":1795000000,\"exp\":4102444800}")
                + "." + parts[2];

        assertInvalid(forged);
    }

    @Test
    void rejectsTamperedSignature() {
        String token = jwtService.generateToken(7L);
        char last = token.charAt(token.length() - 1);
        assertInvalid(token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A'));
    }

    @Test
    void rejectsUnsignedToken() {
        String unsigned = base64Url("{\"alg\":\"none\"}") + "." + base64Url("{\"sub\":\"7\",\"iat\":"
                + NOW.getEpochSecond() + ",\"exp\":" + NOW.plusSeconds(TTL).getEpochSecond() + "}") + ".";
        assertInvalid(unsigned);
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        JwtService other = service(Base64.getEncoder().encodeToString(randomBytes(32)), Clock.fixed(NOW, ZoneOffset.UTC));
        assertInvalid(other.generateToken(7L));
    }

    @Test
    void rejectsTokenSignedWithAnotherAlgorithmEvenWithTheSameKey() {
        // Avec une clé de 64 octets, HS512 serait accepté par la seule vérification de signature.
        byte[] longKey = randomBytes(64);
        JwtService hs256Service = service(Base64.getEncoder().encodeToString(longKey), Clock.fixed(NOW, ZoneOffset.UTC));
        SecretKey key = Keys.hmacShaKeyFor(longKey);
        String hs512 = Jwts.builder().subject("7").issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plusSeconds(TTL))).signWith(key, Jwts.SIG.HS512).compact();

        assertThatThrownBy(() -> hs256Service.extractUserId(hs512)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsTokenWithoutExpiration() {
        String token = Jwts.builder().subject("7").issuedAt(Date.from(NOW))
                .signWith(Keys.hmacShaKeyFor(keyBytes), Jwts.SIG.HS256).compact();
        assertInvalid(token);
    }

    @Test
    void rejectsMalformedTokens() {
        assertInvalid("abc");
        assertInvalid("a.b.c");
        assertInvalid("Bearer " + jwtService.generateToken(7L));
    }

    @Test
    void refusesKeyShorterThan32BytesAfterDecoding() {
        String shortKey = Base64.getEncoder().encodeToString(randomBytes(31));
        assertThatThrownBy(() -> service(shortKey, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("au moins 32 octets");
    }

    @Test
    void refusesKeyThatIsNotBase64() {
        assertThatThrownBy(() -> service("ceci n'est pas du base64 !", Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }

    private void assertInvalid(String token) {
        assertThatThrownBy(() -> jwtService.extractUserId(token))
                .isInstanceOfSatisfying(InvalidTokenException.class,
                        e -> assertThat(e.reason()).isEqualTo(InvalidTokenException.Reason.INVALID));
    }
}
