package sn.ucak.tpapirest.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import sn.ucak.tpapirest.entity.Role;
import sn.ucak.tpapirest.entity.User;
import sn.ucak.tpapirest.security.JwtProperties;
import sn.ucak.tpapirest.security.JwtService;
import sn.ucak.tpapirest.support.AbstractIntegrationTest;

/** Contrôle des tokens (401) et des rôles (403) avec la configuration de sécurité réelle. */
class SecurityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JwtProperties jwtProperties;

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void missingTokenIsUnauthorizedWithJsonError() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentification requise : token absent"))
                .andExpect(jsonPath("$.path").value("/api/products"));
    }

    @Test
    void tamperedTokenIsUnauthorized() throws Exception {
        User user = createStandardUser();
        String[] parts = jwtService.generateToken(user.getId()).split("\\.");
        String forgedPayload = base64Url("{\"sub\":\"" + user.getId() + "\",\"iat\":1,\"exp\":4102444800}");

        mockMvc.perform(get("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + parts[0] + "." + forgedPayload + "." + parts[2]))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Token invalide"));
    }

    @Test
    void unsignedTokenIsUnauthorized() throws Exception {
        User user = createStandardUser();
        long now = Instant.now().getEpochSecond();
        String unsigned = base64Url("{\"alg\":\"none\"}") + "."
                + base64Url("{\"sub\":\"" + user.getId() + "\",\"iat\":" + now + ",\"exp\":" + (now + 900) + "}") + ".";

        mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + unsigned))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void expiredTokenIsUnauthorized() throws Exception {
        User user = createStandardUser();
        // Token émis il y a 16 minutes avec la même clé : il a expiré depuis une minute.
        Clock past = Clock.fixed(Instant.now().minus(Duration.ofMinutes(16)), ZoneOffset.UTC);
        String expired = new JwtService(jwtProperties, past).generateToken(user.getId());

        mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Token expiré"));
    }

    @Test
    void malformedAuthorizationHeadersAreUnauthorized() throws Exception {
        User user = createStandardUser();
        String token = jwtService.generateToken(user.getId());

        mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer "))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer Bearer " + token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, "Basic YWRtaW46YWRtaW4="))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void userCanReadProductsButCannotWriteThemNorManageUsers() throws Exception {
        String userAuth = bearer(createStandardUser());
        String productJson = "{\"name\": \"Clavier\", \"price\": 12.34}";

        mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, userAuth)).andExpect(status().isOk());
        mockMvc.perform(post("/api/products").header(HttpHeaders.AUTHORIZATION, userAuth)
                        .contentType(MediaType.APPLICATION_JSON).content(productJson))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Accès refusé : rôle insuffisant"));
        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, userAuth))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/users").header(HttpHeaders.AUTHORIZATION, userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Pirate\",\"email\":\"p@exemple.test\",\"password\":\"Motdepasse-1\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void userCannotReadOwnAccountThroughAdminRoutes() throws Exception {
        User user = createStandardUser();

        mockMvc.perform(get("/api/users/" + user.getId()).header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/users/" + user.getId()).header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Awa\",\"email\":\"awa.diop@exemple.test\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/users/" + user.getId()).header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessAllRoutes() throws Exception {
        createAdmin();
        String adminAuth = loginBearer("admin@exemple.test", PASSWORD);

        mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, adminAuth)).andExpect(status().isOk());
        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, adminAuth)).andExpect(status().isOk());
        mockMvc.perform(post("/api/products").header(HttpHeaders.AUTHORIZATION, adminAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Clavier\", \"price\": 1}"))
                .andExpect(status().isCreated());
    }

    @Test
    void deletedAccountTokenIsRejected() throws Exception {
        User admin = createAdmin();
        User user = createStandardUser();
        String userAuth = bearer(user);
        mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, userAuth)).andExpect(status().isOk());

        mockMvc.perform(delete("/api/users/" + user.getId()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, userAuth))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void demotedAdminLosesAdminRightsOnNextRequest() throws Exception {
        User admin = createAdmin();
        User secondAdmin = createUser("Second Admin", "second.admin@exemple.test", Role.ADMIN);
        String secondAdminAuth = bearer(secondAdmin);
        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, secondAdminAuth)).andExpect(status().isOk());

        mockMvc.perform(put("/api/users/" + secondAdmin.getId()).header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Second Admin\",\"email\":\"second.admin@exemple.test\",\"role\":\"USER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));

        // Même token, déjà émis : le rôle actuel en base s'applique.
        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, secondAdminAuth))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, secondAdminAuth))
                .andExpect(status().isOk());
    }

    @Test
    void promotedUserGainsAdminRightsOnNextRequest() throws Exception {
        User admin = createAdmin();
        User user = createStandardUser();
        String userAuth = bearer(user);
        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, userAuth)).andExpect(status().isForbidden());

        mockMvc.perform(put("/api/users/" + user.getId()).header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Awa Diop\",\"email\":\"awa.diop@exemple.test\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, userAuth)).andExpect(status().isOk());
    }

    @Test
    void unknownRouteRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/inexistant")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/inexistant").header(HttpHeaders.AUTHORIZATION, bearer(createStandardUser())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
