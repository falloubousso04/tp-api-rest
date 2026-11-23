package sn.ucak.tpapirest.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.SecureRandom;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;

import sn.ucak.tpapirest.entity.Role;
import sn.ucak.tpapirest.entity.User;
import sn.ucak.tpapirest.repository.ProductRepository;
import sn.ucak.tpapirest.repository.UserRepository;
import sn.ucak.tpapirest.security.JwtService;

/**
 * Base des tests d'intégration : application complète (sécurité réelle) sur une base PostgreSQL
 * jetable démarrée par Testcontainers, partagée par toutes les classes de test.
 *
 * <p>La configuration passe par les mêmes variables que l'application (DB_URL, DB_USERNAME,
 * DB_PASSWORD, JWT_SECRET_BASE64) ; la clé JWT est générée aléatoirement à chaque exécution.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final String PASSWORD = "Motdepasse-test-1";

    /** Clé générée une seule fois par exécution : le fournisseur de propriété peut être rappelé. */
    private static final String JWT_SECRET_BASE64 = randomKey();

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("tp_rest_test");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", POSTGRES::getJdbcUrl);
        registry.add("DB_USERNAME", POSTGRES::getUsername);
        registry.add("DB_PASSWORD", POSTGRES::getPassword);
        registry.add("JWT_SECRET_BASE64", () -> JWT_SECRET_BASE64);
        registry.add("JWT_TTL_SECONDS", () -> "900");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected ProductRepository productRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JwtService jwtService;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        productRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    protected User createUser(String name, String email, Role role) {
        return userRepository.save(new User(name, email, passwordEncoder.encode(PASSWORD), role));
    }

    protected User createAdmin() {
        return createUser("Administrateur", "admin@exemple.test", Role.ADMIN);
    }

    protected User createStandardUser() {
        return createUser("Awa Diop", "awa.diop@exemple.test", Role.USER);
    }

    /** Valeur de l'en-tête Authorization pour un compte, avec un token émis par l'application. */
    protected String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user.getId());
    }

    /** Connexion par l'API ; retourne la valeur de l'en-tête Authorization. */
    protected String loginBearer(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }

    protected String passwordHashOf(Long userId) {
        return jdbcTemplate.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, userId);
    }

    protected long countUsers() {
        return userRepository.count();
    }

    protected long countProducts() {
        return productRepository.count();
    }

    private static String randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
