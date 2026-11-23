package sn.ucak.tpapirest.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import sn.ucak.tpapirest.dto.auth.RegisterRequest;
import sn.ucak.tpapirest.dto.product.ProductRequest;
import sn.ucak.tpapirest.dto.user.CreateUserRequest;
import sn.ucak.tpapirest.dto.user.UpdateUserRequest;
import sn.ucak.tpapirest.entity.Role;

/** Cas valides, invalides et aux limites des règles de validation (tableau X.3 du sujet). */
class RequestValidationTest {

    private static final String VALID_PASSWORD = "Motdepasse-1";

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private static Set<String> invalidFields(Object request) {
        Set<ConstraintViolation<Object>> violations = validator.validate(request);
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    private static CreateUserRequest userWithName(String name) {
        return new CreateUserRequest(name, "awa@exemple.test", VALID_PASSWORD, Role.USER);
    }

    private static CreateUserRequest userWithEmail(String email) {
        return new CreateUserRequest("Awa Diop", email, VALID_PASSWORD, Role.USER);
    }

    private static CreateUserRequest userWithPassword(String password) {
        return new CreateUserRequest("Awa Diop", "awa@exemple.test", password, Role.USER);
    }

    private static ProductRequest product(String name, String description, String price) {
        return new ProductRequest(name, description, price == null ? null : new BigDecimal(price));
    }

    /** Email valide de 254 caractères respectant les limites de ses parties (64 + 1 + 189). */
    static String email254() {
        String local = "a".repeat(64);
        String domain = "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(58) + ".sn";
        String email = local + "@" + domain;
        assertThat(email).hasSize(254);
        return email;
    }

    @Nested
    class Names {

        @ParameterizedTest
        @ValueSource(strings = {"Ab", "Clavier", "  Clavier  "})
        void acceptsValidNames(String name) {
            assertThat(invalidFields(userWithName(name))).isEmpty();
            assertThat(invalidFields(product(name, null, "1"))).isEmpty();
        }

        @Test
        void acceptsHundredCharactersAfterTrimming() {
            String name = "  " + "n".repeat(100) + "  ";
            assertThat(invalidFields(userWithName(name))).isEmpty();
            assertThat(userWithName(name).name()).hasSize(100);
        }

        @Test
        void storesTrimmedValue() {
            assertThat(userWithName("  Clavier ").name()).isEqualTo("Clavier");
            assertThat(product("  Clavier ", null, "1").name()).isEqualTo("Clavier");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "A", "  A  "})
        void rejectsMissingBlankOrTooShortNames(String name) {
            assertThat(invalidFields(userWithName(name))).containsExactly("name");
            assertThat(invalidFields(product(name, null, "1"))).containsExactly("name");
        }

        @Test
        void rejectsHundredAndOneCharacters() {
            assertThat(invalidFields(userWithName("n".repeat(101)))).containsExactly("name");
            assertThat(invalidFields(product(" " + "n".repeat(101) + " ", null, "1"))).containsExactly("name");
        }
    }

    @Nested
    class Emails {

        @Test
        void normalizesBeforeValidation() {
            CreateUserRequest request = userWithEmail("  Awa.Diop@Exemple.TEST  ");
            assertThat(request.email()).isEqualTo("awa.diop@exemple.test");
            assertThat(invalidFields(request)).isEmpty();
        }

        @Test
        void acceptsEmailOf254Characters() {
            assertThat(invalidFields(userWithEmail(email254()))).isEmpty();
        }

        @Test
        void rejectsEmailOf255Characters() {
            String email = "a" + email254();
            assertThat(email).hasSize(255);
            assertThat(invalidFields(userWithEmail(email))).containsExactly("email");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "awa", "awa@", "@exemple.test", "awa@exemple", "awa@@exemple.test",
                "awa diop@exemple.test", "awa..diop@exemple.test", "awa@-exemple.test", "awa@exemple.t"})
        void rejectsMissingOrMalformedEmails(String email) {
            assertThat(invalidFields(userWithEmail(email))).containsExactly("email");
        }

        @Test
        void rejectsLocalPartLongerThan64Characters() {
            assertThat(invalidFields(userWithEmail("a".repeat(65) + "@exemple.test"))).containsExactly("email");
        }
    }

    @Nested
    class Passwords {

        @Test
        void acceptsEightAndSeventyTwoCharacters() {
            assertThat(invalidFields(userWithPassword("a".repeat(8)))).isEmpty();
            assertThat(invalidFields(userWithPassword("a".repeat(72)))).isEmpty();
        }

        @Test
        void keepsPasswordContentUnchanged() {
            String password = "  mot de passe  ";
            CreateUserRequest request = userWithPassword(password);
            assertThat(request.password()).isEqualTo(password);
            assertThat(invalidFields(request)).isEmpty();
        }

        @Test
        void rejectsSevenAndSeventyThreeCharacters() {
            assertThat(invalidFields(userWithPassword("a".repeat(7)))).containsExactly("password");
            assertThat(invalidFields(userWithPassword("a".repeat(73)))).containsExactly("password");
        }

        @Test
        void reportsMinimumAndMaximumWithDistinctMessages() {
            String tooShort = validator.validate(userWithPassword("a".repeat(7))).iterator().next().getMessage();
            String tooLong = validator.validate(userWithPassword("a".repeat(73))).iterator().next().getMessage();
            assertThat(tooShort).contains("au moins 8");
            assertThat(tooLong).contains("au plus 72");
        }

        @Test
        void countsUnicodeCharactersNotUtf16Units() {
            // 8 émojis = 8 caractères Unicode (16 unités UTF-16) : accepté.
            assertThat(invalidFields(userWithPassword("😀".repeat(8)))).isEmpty();
            // 7 émojis = 7 caractères Unicode : refusé même si length() vaut 14.
            assertThat(invalidFields(userWithPassword("😀".repeat(7)))).containsExactly("password");
        }

        @Test
        void rejectsPasswordExceedingBcryptByteLimit() {
            // 40 caractères mais 80 octets UTF-8 : BCrypt ne prendrait en compte que 72 octets.
            assertThat(invalidFields(userWithPassword("é".repeat(40)))).containsExactly("password");
        }

        @Test
        void requiresPasswordOnCreationOnly() {
            assertThat(invalidFields(userWithPassword(null))).containsExactly("password");
            assertThat(invalidFields(new RegisterRequest("Awa Diop", "awa@exemple.test", null)))
                    .containsExactly("password");
            assertThat(invalidFields(new UpdateUserRequest("Awa Diop", "awa@exemple.test", Role.USER, null)))
                    .isEmpty();
        }

        @Test
        void validatesPasswordWhenProvidedOnUpdate() {
            assertThat(invalidFields(new UpdateUserRequest("Awa Diop", "awa@exemple.test", Role.USER, "court")))
                    .containsExactly("password");
        }
    }

    @Nested
    class Roles {

        @Test
        void requiresRoleOnAdministrativeCreationAndUpdate() {
            assertThat(invalidFields(new CreateUserRequest("Awa Diop", "awa@exemple.test", VALID_PASSWORD, null)))
                    .containsExactly("role");
            assertThat(invalidFields(new UpdateUserRequest("Awa Diop", "awa@exemple.test", null, null)))
                    .containsExactly("role");
        }

        @Test
        void acceptsUserAndAdmin() {
            assertThat(invalidFields(new CreateUserRequest("Awa Diop", "awa@exemple.test", VALID_PASSWORD, Role.ADMIN)))
                    .isEmpty();
            assertThat(invalidFields(new UpdateUserRequest("Awa Diop", "awa@exemple.test", Role.USER, null)))
                    .isEmpty();
        }
    }

    @Nested
    class Descriptions {

        @Test
        void acceptsAbsentEmptyAndThousandCharacters() {
            assertThat(invalidFields(product("Clavier", null, "1"))).isEmpty();
            assertThat(invalidFields(product("Clavier", "", "1"))).isEmpty();
            assertThat(invalidFields(product("Clavier", "d".repeat(1000), "1"))).isEmpty();
        }

        @Test
        void rejectsThousandAndOneCharactersWithoutTruncating() {
            ProductRequest request = product("Clavier", "d".repeat(1001), "1");
            assertThat(invalidFields(request)).containsExactly("description");
            assertThat(request.description()).hasSize(1001);
        }
    }

    @Nested
    class Prices {

        @ParameterizedTest
        @ValueSource(strings = {"0.01", "1", "12.34", "12.3", "9999999999.99"})
        void acceptsValidPrices(String price) {
            assertThat(invalidFields(product("Clavier", null, price))).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "0.00", "-1", "-0.01", "1.234", "1.230", "0.001", "12345678901"})
        void rejectsInvalidPrices(String price) {
            assertThat(invalidFields(product("Clavier", null, price))).containsExactly("price");
        }

        @Test
        void rejectsMissingPrice() {
            assertThat(invalidFields(product("Clavier", null, null))).containsExactly("price");
        }
    }
}
