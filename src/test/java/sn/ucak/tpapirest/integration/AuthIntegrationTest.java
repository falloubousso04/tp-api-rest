package sn.ucak.tpapirest.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import sn.ucak.tpapirest.entity.Role;
import sn.ucak.tpapirest.entity.User;
import sn.ucak.tpapirest.support.AbstractIntegrationTest;

/** Inscription, connexion, absence de session et accès public à la documentation. */
class AuthIntegrationTest extends AbstractIntegrationTest {

    @Test
    void registerCreatesUserAccountWithNormalizedValues() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "  Awa Diop  ", "email": "  Awa.Diop@Exemple.TEST ", "password": " mot de passe "}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/users/")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Awa Diop"))
                .andExpect(jsonPath("$.email").value("awa.diop@exemple.test"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("$2a$");
        User saved = userRepository.findByEmail("awa.diop@exemple.test").orElseThrow();
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).endsWith("/api/users/" + saved.getId());
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(passwordEncoder.matches(" mot de passe ", saved.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("mot de passe", saved.getPasswordHash())).isFalse();
    }

    @Test
    void registerWithRoleIsRefusedAndCreatesNothing() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Awa Diop", "email": "awa@exemple.test", "password": "Motdepasse-1", "role": "ADMIN"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.role").exists());

        assertThat(countUsers()).isZero();
    }

    @Test
    void registerWithUserRoleIsAlsoRefused() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Awa Diop", "email": "awa@exemple.test", "password": "Motdepasse-1", "role": "USER"}
                                """))
                .andExpect(status().isBadRequest());

        assertThat(countUsers()).isZero();
    }

    @Test
    void registerWithEmailAlreadyUsedAfterNormalizationIsConflict() throws Exception {
        createStandardUser();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Autre", "email": " AWA.DIOP@exemple.test ", "password": "Motdepasse-1"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errors.email").exists());

        assertThat(countUsers()).isEqualTo(1);
    }

    @Test
    void registerWithInvalidDataReturnsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": " ", "email": "pas-un-email", "password": "court"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Données invalides"))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.password", not(containsString("court"))));

        assertThat(countUsers()).isZero();
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Awa\", "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("pas du json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void loginReturnsBearerTokenValidFor900Seconds() throws Exception {
        User user = createStandardUser();

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"  AWA.Diop@exemple.test \", \"password\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn();

        String token = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
        assertThat(jwtService.extractUserId(token)).isEqualTo(user.getId());
    }

    @Test
    void unknownEmailAndWrongPasswordGiveTheSameGenericMessage() throws Exception {
        createStandardUser();

        String unknownEmail = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"inconnu@exemple.test\", \"password\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andReturn().getResponse().getContentAsString();

        String wrongPassword = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"awa.diop@exemple.test\", \"password\": \"Mauvais-mdp-1\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String message = "Email ou mot de passe incorrect";
        assertThat(unknownEmail).contains(message).doesNotContain("Mauvais");
        assertThat(wrongPassword).contains(message).doesNotContain("Mauvais-mdp-1");
    }

    @Test
    void loginWithPasswordDifferingOnlyBySpacesIsRefused() throws Exception {
        createStandardUser();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"awa.diop@exemple.test\", \"password\": \" " + PASSWORD + " \"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithMissingFieldsIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"awa.diop@exemple.test\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void noAuthenticationSessionOrCookieIsCreated() throws Exception {
        User user = createStandardUser();

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"awa.diop@exemple.test\", \"password\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn();
        assertThat(login.getRequest().getSession(false)).isNull();

        MvcResult authenticated = mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn();
        assertThat(authenticated.getRequest().getSession(false)).isNull();

        // Une requête suivante sans token n'est pas authentifiée : aucun état n'a été conservé.
        mockMvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
    }

    @Test
    void documentationIsPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.paths['/api/products'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/auth/register'].post.security").isEmpty());

        mockMvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.LOCATION, endsWith("/swagger-ui/index.html")));
    }
}
