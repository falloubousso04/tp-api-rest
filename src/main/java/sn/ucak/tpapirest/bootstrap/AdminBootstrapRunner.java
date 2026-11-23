package sn.ucak.tpapirest.bootstrap;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Initialise le premier ADMIN au démarrage, uniquement avec le profil {@code bootstrap}
 * (désactivé par défaut, activé localement en complément de {@code tp}).
 *
 * <p>Les valeurs proviennent des variables d'environnement temporaires
 * {@code BOOTSTRAP_ADMIN_NAME}, {@code BOOTSTRAP_ADMIN_EMAIL} et {@code BOOTSTRAP_ADMIN_PASSWORD}.
 * Il n'existe ni endpoint d'initialisation ni mot de passe par défaut. Toute erreur arrête
 * le démarrage avec un message explicite (sans jamais afficher le mot de passe).</p>
 */
@Component
@Profile("bootstrap")
public class AdminBootstrapRunner implements ApplicationRunner {

    static final String NAME_VARIABLE = "BOOTSTRAP_ADMIN_NAME";
    static final String EMAIL_VARIABLE = "BOOTSTRAP_ADMIN_EMAIL";
    static final String PASSWORD_VARIABLE = "BOOTSTRAP_ADMIN_PASSWORD";

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final Environment environment;
    private final AdminBootstrapService bootstrapService;

    public AdminBootstrapRunner(Environment environment, AdminBootstrapService bootstrapService) {
        this.environment = environment;
        this.bootstrapService = bootstrapService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> missing = new ArrayList<>();
        String name = read(NAME_VARIABLE, missing);
        String email = read(EMAIL_VARIABLE, missing);
        String password = read(PASSWORD_VARIABLE, missing);
        if (!missing.isEmpty()) {
            throw new BootstrapException("variable(s) non définie(s) : " + String.join(", ", missing));
        }

        AdminBootstrapService.Result result = bootstrapService.initializeFirstAdmin(name, email, password);
        switch (result.outcome()) {
            case CREATED -> log.info("Premier administrateur créé (id={}, email={}). Arrêtez l'application, "
                            + "retirez le profil bootstrap et les variables BOOTSTRAP_ADMIN_*, "
                            + "puis redémarrez avec le profil tp seul.",
                    result.admin().getId(), result.admin().getEmail());
            case ADMIN_ALREADY_EXISTS -> log.info("Un administrateur existe déjà : aucun compte n'a été créé "
                    + "ni modifié. Retirez le profil bootstrap et les variables BOOTSTRAP_ADMIN_*.");
        }
    }

    /** Lit une variable sans jamais en afficher la valeur ; mémorise son nom si elle est absente. */
    private String read(String variable, List<String> missing) {
        String value = environment.getProperty(variable);
        if (value == null || value.isEmpty()) {
            missing.add(variable);
        }
        return value;
    }
}
