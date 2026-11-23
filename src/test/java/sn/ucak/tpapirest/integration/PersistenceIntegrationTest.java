package sn.ucak.tpapirest.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import sn.ucak.tpapirest.entity.Product;
import sn.ucak.tpapirest.entity.Role;
import sn.ucak.tpapirest.entity.User;
import sn.ucak.tpapirest.support.AbstractIntegrationTest;

/**
 * Mapping, contraintes et persistance vérifiés directement sur PostgreSQL, en contournant
 * les contrôles préalables des services.
 */
class PersistenceIntegrationTest extends AbstractIntegrationTest {

    @Test
    void databaseRefusesDuplicateNormalizedEmailWithoutServiceCheck() {
        userRepository.saveAndFlush(new User("Awa", "awa@exemple.test", passwordEncoder.encode(PASSWORD), Role.USER));

        assertThatThrownBy(() -> userRepository.saveAndFlush(
                new User("Autre", "awa@exemple.test", passwordEncoder.encode(PASSWORD), Role.ADMIN)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(User.EMAIL_UNIQUE_CONSTRAINT);
        assertThat(countUsers()).isEqualTo(1);
    }

    @Test
    void databaseRefusesEmailThatIsNotNormalized() {
        assertThatThrownBy(() -> userRepository.saveAndFlush(
                new User("Awa", " Awa@Exemple.test", passwordEncoder.encode(PASSWORD), Role.USER)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRefusesNonPositivePrice() {
        assertThatThrownBy(() -> productRepository.saveAndFlush(new Product("Clavier", null, BigDecimal.ZERO)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void identifiersAreGeneratedByTheDatabase() {
        Product first = productRepository.saveAndFlush(new Product("Clavier", null, new BigDecimal("1.00")));
        Product second = productRepository.saveAndFlush(new Product("Souris", null, new BigDecimal("2.00")));

        assertThat(first.getId()).isNotNull();
        assertThat(second.getId()).isGreaterThan(first.getId());
    }

    @Test
    void schemaMatchesTheExpectedMapping() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList("""
                SELECT table_name, column_name, data_type, character_maximum_length, numeric_precision,
                       numeric_scale, is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name IN ('users', 'products')
                """);

        assertThat(column(columns, "users", "email"))
                .containsEntry("character_maximum_length", 254).containsEntry("is_nullable", "NO");
        assertThat(column(columns, "users", "name")).containsEntry("character_maximum_length", 100);
        assertThat(column(columns, "users", "role")).containsEntry("data_type", "character varying");
        assertThat(column(columns, "users", "password_hash")).containsEntry("is_nullable", "NO");
        assertThat(column(columns, "products", "price"))
                .containsEntry("data_type", "numeric").containsEntry("numeric_scale", 2).containsEntry("is_nullable", "NO");
        assertThat(column(columns, "products", "description"))
                .containsEntry("character_maximum_length", 1000).containsEntry("is_nullable", "YES");

        List<String> constraints = jdbcTemplate.queryForList(
                "SELECT conname FROM pg_constraint WHERE conrelid IN ('users'::regclass, 'products'::regclass)",
                String.class);
        assertThat(constraints).contains(User.EMAIL_UNIQUE_CONSTRAINT, "ck_users_email_normalized",
                "ck_products_price_positive");
    }

    @Test
    void dataIsPersistedAndReadBackExactly() {
        User user = userRepository.saveAndFlush(
                new User("Awa Diop", "awa@exemple.test", passwordEncoder.encode(PASSWORD), Role.ADMIN));
        Product product = productRepository.saveAndFlush(new Product("Clavier", "AZERTY", new BigDecimal("12.34")));

        Map<String, Object> userRow = jdbcTemplate.queryForMap("SELECT name, email, role FROM users WHERE id = ?", user.getId());
        assertThat(userRow).containsEntry("name", "Awa Diop").containsEntry("email", "awa@exemple.test")
                .containsEntry("role", "ADMIN");
        BigDecimal price = jdbcTemplate.queryForObject("SELECT price FROM products WHERE id = ?", BigDecimal.class,
                product.getId());
        assertThat(price).isEqualTo(new BigDecimal("12.34"));
    }

    private static Map<String, Object> column(List<Map<String, Object>> columns, String table, String name) {
        return columns.stream()
                .filter(c -> table.equals(c.get("table_name")) && name.equals(c.get("column_name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Colonne absente : " + table + "." + name));
    }
}
