package sn.ucak.tpapirest.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

/**
 * Refuse le démarrage de l'application lorsqu'un paramètre indispensable est absent.
 *
 * <p>Spring Boot laisse tels quels les marqueurs {@code ${VARIABLE}} qu'il ne sait pas
 * résoudre lors de la liaison des propriétés. Sans ce contrôle, l'application tenterait
 * par exemple de se connecter avec le texte littéral {@code ${DB_PASSWORD}}.
 * Ce contrôle s'exécute avant la création de tout autre bean : aucune connexion n'est
 * ouverte et aucune valeur de secret n'est affichée.</p>
 */
public class RequiredConfigurationValidator implements BeanFactoryPostProcessor, EnvironmentAware, Ordered {

    /** Propriété Spring → variable d'environnement attendue. */
    private static final Map<String, String> REQUIRED_PROPERTIES = new LinkedHashMap<>();

    static {
        REQUIRED_PROPERTIES.put("spring.datasource.url", "DB_URL");
        REQUIRED_PROPERTIES.put("spring.datasource.username", "DB_USERNAME");
        REQUIRED_PROPERTIES.put("spring.datasource.password", "DB_PASSWORD");
        REQUIRED_PROPERTIES.put("app.jwt.secret-base64", "JWT_SECRET_BASE64");
    }

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        List<String> missing = new ArrayList<>();
        REQUIRED_PROPERTIES.forEach((property, variable) -> {
            if (!isDefined(property)) {
                missing.add(variable);
            }
        });
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Démarrage refusé : variable(s) d'environnement obligatoire(s) non définie(s) : "
                            + String.join(", ", missing)
                            + ". Consultez le README (section « Variables d'environnement »).");
        }
    }

    private boolean isDefined(String property) {
        try {
            String value = environment.getProperty(property);
            return value != null && !value.isBlank();
        } catch (IllegalArgumentException unresolvedPlaceholder) {
            return false;
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
