package sn.ucak.tpapirest.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

class RequiredConfigurationValidatorTest {

    private static MockEnvironment applicationProperties() {
        // Mêmes marqueurs que dans application.yml
        return new MockEnvironment()
                .withProperty("spring.datasource.url", "${DB_URL}")
                .withProperty("spring.datasource.username", "${DB_USERNAME}")
                .withProperty("spring.datasource.password", "${DB_PASSWORD}")
                .withProperty("app.jwt.secret-base64", "${JWT_SECRET_BASE64}");
    }

    private static void validate(MockEnvironment environment) {
        RequiredConfigurationValidator validator = new RequiredConfigurationValidator();
        validator.setEnvironment(environment);
        validator.postProcessBeanFactory(new DefaultListableBeanFactory());
    }

    @Test
    void refusesToStartWhenSecretsAreMissing() {
        MockEnvironment environment = applicationProperties()
                .withProperty("DB_URL", "jdbc:postgresql://localhost:5432/tp_rest")
                .withProperty("DB_USERNAME", "tp_api");

        assertThatThrownBy(() -> validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD")
                .hasMessageContaining("JWT_SECRET_BASE64")
                .hasMessageNotContaining("DB_URL");
    }

    @Test
    void refusesBlankValues() {
        MockEnvironment environment = applicationProperties()
                .withProperty("DB_URL", "jdbc:postgresql://localhost:5432/tp_rest")
                .withProperty("DB_USERNAME", "tp_api")
                .withProperty("DB_PASSWORD", "valeur-de-test")
                .withProperty("JWT_SECRET_BASE64", "  ");

        assertThatThrownBy(() -> validate(environment)).hasMessageContaining("JWT_SECRET_BASE64");
    }

    @Test
    void acceptsCompleteConfiguration() {
        MockEnvironment environment = applicationProperties()
                .withProperty("DB_URL", "jdbc:postgresql://localhost:5432/tp_rest")
                .withProperty("DB_USERNAME", "tp_api")
                .withProperty("DB_PASSWORD", "valeur-de-test")
                .withProperty("JWT_SECRET_BASE64", "dmFsZXVyLWRlLXRlc3QtZGUtMzItb2N0ZXRzLW1pbmltdW0=");

        assertThatCode(() -> validate(environment)).doesNotThrowAnyException();
    }
}
