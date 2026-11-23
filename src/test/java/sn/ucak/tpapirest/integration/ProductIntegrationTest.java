package sn.ucak.tpapirest.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import sn.ucak.tpapirest.entity.Product;
import sn.ucak.tpapirest.support.AbstractIntegrationTest;

/** CRUD des produits, validations sur POST et PUT, statuts HTTP et droits. */
class ProductIntegrationTest extends AbstractIntegrationTest {

    private String adminAuth;
    private String userAuth;

    @BeforeEach
    void createAccounts() {
        adminAuth = bearer(createAdmin());
        userAuth = bearer(createStandardUser());
    }

    private ResultActions postProduct(String auth, String json) throws Exception {
        return mockMvc.perform(post("/api/products").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions putProduct(String auth, Object id, String json) throws Exception {
        return mockMvc.perform(put("/api/products/" + id).header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Product existingProduct() {
        return productRepository.save(new Product("Clavier", "Clavier AZERTY", new BigDecimal("12.34")));
    }

    @Test
    void adminCompleteLifecycle() throws Exception {
        // Création
        String body = postProduct(adminAuth, """
                {"name": "  Clavier  ", "description": "Clavier mécanique", "price": 12.34}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Clavier"))
                .andExpect(jsonPath("$.description").value("Clavier mécanique"))
                .andExpect(jsonPath("$.price").value(12.34))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(body, "$.id");

        // Consultation de la liste et du détail
        mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id));
        mockMvc.perform(get("/api/products/" + id).header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Clavier"));

        // Modification (description absente)
        putProduct(adminAuth, id, "{\"name\": \"Clavier sans fil\", \"price\": 19.99}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Clavier sans fil"))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.price").value(19.99));

        // Suppression puis 404 en consultation et en seconde suppression
        mockMvc.perform(delete("/api/products/" + id).header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mockMvc.perform(get("/api/products/" + id).header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mockMvc.perform(delete("/api/products/" + id).header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isNotFound());
        assertThat(countProducts()).isZero();
    }

    @Test
    void creationReturnsLocationOfTheNewProduct() throws Exception {
        String body = postProduct(adminAuth, "{\"name\": \"Souris\", \"price\": 5}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(body, "$.id");

        postProduct(adminAuth, "{\"name\": \"Écran\", \"price\": 150}")
                .andExpect(header().string(HttpHeaders.LOCATION, endsWith("/api/products/" + (id + 1))));
    }

    @Test
    void emptyListReturnsEmptyArray() throws Exception {
        mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, userAuth))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void userCanReadButEveryWriteIsForbiddenWithoutChangingTheDatabase() throws Exception {
        Product product = existingProduct();

        mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, userAuth))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/api/products/" + product.getId()).header(HttpHeaders.AUTHORIZATION, userAuth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Clavier"));

        postProduct(userAuth, "{\"name\": \"Souris\", \"price\": 5}").andExpect(status().isForbidden());
        putProduct(userAuth, product.getId(), "{\"name\": \"Modifié\", \"price\": 1}").andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/products/" + product.getId()).header(HttpHeaders.AUTHORIZATION, userAuth))
                .andExpect(status().isForbidden());

        assertThat(countProducts()).isEqualTo(1);
        Product unchanged = productRepository.findById(product.getId()).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo("Clavier");
        assertThat(unchanged.getPrice()).isEqualByComparingTo("12.34");
    }

    @Test
    void absentIdentifierIsNotFoundForGetPutAndDelete() throws Exception {
        mockMvc.perform(get("/api/products/999999").header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isNotFound());
        putProduct(adminAuth, 999999, "{\"name\": \"Clavier\", \"price\": 1}")
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/products/999999").header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isNotFound());

        // Un PUT ne crée pas de ressource absente.
        assertThat(countProducts()).isZero();
    }

    @Test
    void identifierThatIsNotANumberIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/products/abc").header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.id").exists());
        mockMvc.perform(delete("/api/products/1.5").header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "1", "12.34"})
    void storesExactlyTheAcceptedPrice(String price) throws Exception {
        String body = postProduct(adminAuth, "{\"name\": \"Clavier\", \"price\": " + price + "}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(body, "$.id")).longValue();

        BigDecimal stored = jdbcTemplate.queryForObject("SELECT price FROM products WHERE id = ?", BigDecimal.class, id);
        assertThat(stored).isEqualByComparingTo(price);
        assertThat(stored.scale()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.234", "1.230", "null", "\"abc\""})
    void invalidPricesAreRefusedWithoutCreation(String price) throws Exception {
        postProduct(adminAuth, "{\"name\": \"Clavier\", \"price\": " + price + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.price").exists());
        assertThat(countProducts()).isZero();
    }

    @Test
    void missingPriceAndNameAreRefused() throws Exception {
        postProduct(adminAuth, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.price").exists());
        postProduct(adminAuth, "{\"name\": \"A\", \"price\": 1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
        assertThat(countProducts()).isZero();
    }

    @Test
    void descriptionLimits() throws Exception {
        postProduct(adminAuth, "{\"name\": \"Clavier\", \"description\": \"\", \"price\": 1}")
                .andExpect(status().isCreated());
        postProduct(adminAuth, "{\"name\": \"Clavier\", \"description\": null, \"price\": 1}")
                .andExpect(status().isCreated());
        postProduct(adminAuth, "{\"name\": \"Clavier\", \"description\": \"" + "d".repeat(1000) + "\", \"price\": 1}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("d".repeat(1000)));
        postProduct(adminAuth, "{\"name\": \"Clavier\", \"description\": \"" + "d".repeat(1001) + "\", \"price\": 1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.description").exists());
        assertThat(countProducts()).isEqualTo(3);
    }

    @Test
    void identifierCannotBeChosenByTheClient() throws Exception {
        postProduct(adminAuth, "{\"id\": 5000, \"name\": \"Clavier\", \"price\": 1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.id").exists());
        assertThat(countProducts()).isZero();
    }

    @Test
    void invalidUpdateLeavesProductUnchanged() throws Exception {
        Product product = existingProduct();

        putProduct(adminAuth, product.getId(), "{\"name\": \" \", \"price\": 1.230}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.price").exists());
        putProduct(adminAuth, product.getId(), "{\"name\": \"Clavier\"}")
                .andExpect(status().isBadRequest());

        Product unchanged = productRepository.findById(product.getId()).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo("Clavier");
        assertThat(unchanged.getDescription()).isEqualTo("Clavier AZERTY");
        assertThat(unchanged.getPrice()).isEqualByComparingTo("12.34");
    }

    @Test
    void updateCanRemoveTheDescription() throws Exception {
        Product product = existingProduct();

        putProduct(adminAuth, product.getId(), "{\"name\": \"Clavier\", \"description\": null, \"price\": 12.34}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").doesNotExist());
        assertThat(productRepository.findById(product.getId()).orElseThrow().getDescription()).isNull();
    }
}
