package sn.ucak.tpapirest.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import sn.ucak.tpapirest.bootstrap.AdminBootstrapService;
import sn.ucak.tpapirest.bootstrap.BootstrapException;
import sn.ucak.tpapirest.entity.Role;
import sn.ucak.tpapirest.entity.User;
import sn.ucak.tpapirest.support.AbstractIntegrationTest;

/** Initialisation du premier ADMIN (logique du profil bootstrap). */
class AdminBootstrapIntegrationTest extends AbstractIntegrationTest {

    private static final String BOOTSTRAP_PASSWORD = "Admin-fictif-2026";

    @Autowired
    private AdminBootstrapService bootstrapService;

    private List<Map<String, Object>> snapshotUsers() {
        return jdbcTemplate.queryForList("SELECT id, name, email, role, password_hash FROM users ORDER BY id");
    }

    @Test
    void createsTheFirstAdminWithNormalizationAndBcrypt() throws Exception {
        AdminBootstrapService.Result result =
                bootstrapService.initializeFirstAdmin("  Administrateur ", " Admin@Exemple.TEST ", BOOTSTRAP_PASSWORD);

        assertThat(result.outcome()).isEqualTo(AdminBootstrapService.Outcome.CREATED);
        User admin = userRepository.findByEmail("admin@exemple.test").orElseThrow();
        assertThat(admin.getName()).isEqualTo("Administrateur");
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getPasswordHash()).startsWith("$2a$").isNotEqualTo(BOOTSTRAP_PASSWORD);

        // Le compte créé peut se connecter et accéder aux routes ADMIN.
        loginBearer("admin@exemple.test", BOOTSTRAP_PASSWORD);
    }

    @Test
    void doesNotModifyAnyAccountWhenAnAdminAlreadyExists() {
        createAdmin();
        createStandardUser();
        List<Map<String, Object>> before = snapshotUsers();

        AdminBootstrapService.Result result =
                bootstrapService.initializeFirstAdmin("Autre Admin", "awa.diop@exemple.test", BOOTSTRAP_PASSWORD);

        assertThat(result.outcome()).isEqualTo(AdminBootstrapService.Outcome.ADMIN_ALREADY_EXISTS);
        assertThat(snapshotUsers()).isEqualTo(before);
    }

    @Test
    void stopsWhenEmailBelongsToAnExistingUserWithoutPromotingIt() {
        User user = createStandardUser();
        List<Map<String, Object>> before = snapshotUsers();

        assertThatThrownBy(() ->
                bootstrapService.initializeFirstAdmin("Admin", " AWA.DIOP@exemple.test ", BOOTSTRAP_PASSWORD))
                .isInstanceOf(BootstrapException.class)
                .hasMessageContaining("déjà utilisé");

        assertThat(snapshotUsers()).isEqualTo(before);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getRole()).isEqualTo(Role.USER);
        assertThat(userRepository.existsByRole(Role.ADMIN)).isFalse();
    }

    @Test
    void appliesTheSameValidationsAndNeverRevealsThePassword() {
        String invalidPassword = "court";

        assertThatThrownBy(() -> bootstrapService.initializeFirstAdmin("A", "pas-un-email", invalidPassword))
                .isInstanceOf(BootstrapException.class)
                .hasMessageContaining("name")
                .hasMessageContaining("email")
                .hasMessageContaining("password")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(invalidPassword));

        assertThat(countUsers()).isZero();
    }
}
