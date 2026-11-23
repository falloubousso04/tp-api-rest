package sn.ucak.tpapirest.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import sn.ucak.tpapirest.entity.Role;
import sn.ucak.tpapirest.entity.User;
import sn.ucak.tpapirest.support.AbstractIntegrationTest;

/** CRUD des utilisateurs par ADMIN : rôles, mots de passe, unicité de l'email. */
class UserIntegrationTest extends AbstractIntegrationTest {

    private User admin;
    private String adminAuth;

    @BeforeEach
    void createAdministrator() {
        admin = createAdmin();
        adminAuth = bearer(admin);
    }

    private ResultActions postUser(String json) throws Exception {
        return mockMvc.perform(post("/api/users").header(HttpHeaders.AUTHORIZATION, adminAuth)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions putUser(Object id, String json) throws Exception {
        return mockMvc.perform(put("/api/users/" + id).header(HttpHeaders.AUTHORIZATION, adminAuth)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static void assertNoSecret(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("password").doesNotContain("$2a$").doesNotContain("$2b$");
    }

    @Test
    void adminCompleteLifecycle() throws Exception {
        // Création avec rôle
        MvcResult created = postUser("""
                {"name": "Moussa Ndiaye", "email": "Moussa.Ndiaye@Exemple.test", "password": "Motdepasse-1", "role": "ADMIN"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("moussa.ndiaye@exemple.test"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn();
        assertNoSecret(created);
        Integer id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        assertThat(created.getResponse().getHeader(HttpHeaders.LOCATION)).endsWith("/api/users/" + id);

        // Liste et détail
        MvcResult list = mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andReturn();
        assertNoSecret(list);
        mockMvc.perform(get("/api/users/" + id).header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Moussa Ndiaye"))
                .andExpect(jsonPath("$.password").doesNotExist());

        // Modification
        putUser(id, "{\"name\": \"Moussa N.\", \"email\": \"moussa.ndiaye@exemple.test\", \"role\": \"USER\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Moussa N."))
                .andExpect(jsonPath("$.role").value("USER"));

        // Suppression puis 404
        mockMvc.perform(delete("/api/users/" + id).header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mockMvc.perform(get("/api/users/" + id).header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/users/" + id).header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isNotFound());
    }

    @Test
    void createdPasswordIsHashedWithBcrypt() throws Exception {
        String body = postUser("""
                {"name": "Moussa", "email": "moussa@exemple.test", "password": " avec espaces ", "role": "USER"}
                """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(body, "$.id")).longValue();

        String hash = passwordHashOf(id);
        assertThat(hash).startsWith("$2a$").hasSize(60);
        assertThat(passwordEncoder.matches(" avec espaces ", hash)).isTrue();
    }

    @Test
    void creationWithEmailAlreadyUsedAfterNormalizationIsConflict() throws Exception {
        createStandardUser();

        postUser("{\"name\": \"Autre\", \"email\": \"  AWA.DIOP@EXEMPLE.TEST \", \"password\": \"Motdepasse-1\", \"role\": \"USER\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errors.email").exists());
        assertThat(countUsers()).isEqualTo(2);
    }

    @Test
    void invalidCreationsAreRefusedWithoutChangingTheDatabase() throws Exception {
        postUser("{\"name\": \"Moussa\", \"email\": \"moussa@exemple.test\", \"password\": \"Motdepasse-1\", \"role\": \"SUPERADMIN\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.role").exists());
        postUser("{\"name\": \"Moussa\", \"email\": \"moussa@exemple.test\", \"password\": \"Motdepasse-1\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.role").exists());
        postUser("{\"name\": \"Moussa\", \"email\": \"moussa@exemple.test\", \"role\": \"USER\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
        postUser("{\"name\": \"Moussa\", \"email\": \"moussa@exemple.test\", \"password\": \"" + "a".repeat(73) + "\", \"role\": \"USER\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
        postUser("{\"name\": \"Moussa\", \"email\": \"" + "a".repeat(250) + "@x.sn\", \"password\": \"Motdepasse-1\", \"role\": \"USER\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());

        assertThat(countUsers()).isEqualTo(1);
    }

    @Test
    void userCanKeepOwnEmailButNotTakeAnotherAccountEmail() throws Exception {
        User user = createStandardUser();
        createUser("Moussa Ndiaye", "moussa@exemple.test", Role.USER);

        putUser(user.getId(), "{\"name\": \"Awa D.\", \"email\": \" AWA.DIOP@exemple.test\", \"role\": \"USER\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("awa.diop@exemple.test"));

        putUser(user.getId(), "{\"name\": \"Awa D.\", \"email\": \"MOUSSA@exemple.test\", \"role\": \"USER\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.email").exists());
        assertThat(userRepository.findById(user.getId()).orElseThrow().getEmail()).isEqualTo("awa.diop@exemple.test");
    }

    @Test
    void omittedPasswordKeepsTheExistingHash() throws Exception {
        User user = createStandardUser();
        String hashBefore = passwordHashOf(user.getId());

        putUser(user.getId(), "{\"name\": \"Awa Diop\", \"email\": \"awa.diop@exemple.test\", \"role\": \"USER\"}")
                .andExpect(status().isOk());

        assertThat(passwordHashOf(user.getId())).isEqualTo(hashBefore);
        loginBearer("awa.diop@exemple.test", PASSWORD);
    }

    @Test
    void passwordChangeAllowsLoginWithNewValueAndRefusesTheOldOne() throws Exception {
        User user = createStandardUser();
        String hashBefore = passwordHashOf(user.getId());

        putUser(user.getId(), """
                {"name": "Awa Diop", "email": "awa.diop@exemple.test", "role": "USER", "password": "Nouveau-mdp-2026"}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.password").doesNotExist());

        String hashAfter = passwordHashOf(user.getId());
        assertThat(hashAfter).isNotEqualTo(hashBefore).startsWith("$2a$");
        loginBearer("awa.diop@exemple.test", "Nouveau-mdp-2026");
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"awa.diop@exemple.test\", \"password\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nullOrInvalidPasswordOnUpdateIsRefusedWithoutChange() throws Exception {
        User user = createStandardUser();
        String hashBefore = passwordHashOf(user.getId());

        putUser(user.getId(), "{\"name\": \"Changé\", \"email\": \"awa.diop@exemple.test\", \"role\": \"ADMIN\", \"password\": null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
        putUser(user.getId(), "{\"name\": \"Changé\", \"email\": \"awa.diop@exemple.test\", \"role\": \"ADMIN\", \"password\": \"court\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());

        User unchanged = userRepository.findById(user.getId()).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo("Awa Diop");
        assertThat(unchanged.getRole()).isEqualTo(Role.USER);
        assertThat(passwordHashOf(user.getId())).isEqualTo(hashBefore);
    }

    @Test
    void unknownOrMissingRoleOnUpdateIsRefusedWithoutChange() throws Exception {
        User user = createStandardUser();

        putUser(user.getId(), "{\"name\": \"Awa Diop\", \"email\": \"awa.diop@exemple.test\", \"role\": \"ROOT\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.role").exists());
        putUser(user.getId(), "{\"name\": \"Awa Diop\", \"email\": \"awa.diop@exemple.test\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.role").exists());

        assertThat(userRepository.findById(user.getId()).orElseThrow().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void updateOfAbsentAccountIsNotFoundAndCreatesNothing() throws Exception {
        putUser(999999, "{\"name\": \"Fantôme\", \"email\": \"fantome@exemple.test\", \"role\": \"USER\", \"password\": \"Motdepasse-1\"}")
                .andExpect(status().isNotFound());
        assertThat(userRepository.existsByEmail("fantome@exemple.test")).isFalse();
    }

    @Test
    void identifierThatIsNotANumberIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/users/abc").header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.id").exists());
    }

    @Test
    void storedRoleIsTheLabel() throws Exception {
        postUser("{\"name\": \"Moussa\", \"email\": \"moussa@exemple.test\", \"password\": \"Motdepasse-1\", \"role\": \"USER\"}")
                .andExpect(status().isCreated());
        String role = jdbcTemplate.queryForObject("SELECT role FROM users WHERE email = 'moussa@exemple.test'", String.class);
        assertThat(role).isEqualTo("USER");
    }
}
