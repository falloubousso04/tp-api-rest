package sn.ucak.tpapirest.bootstrap;

import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import sn.ucak.tpapirest.dto.user.CreateUserRequest;
import sn.ucak.tpapirest.entity.Role;
import sn.ucak.tpapirest.entity.User;
import sn.ucak.tpapirest.repository.UserRepository;
import sn.ucak.tpapirest.service.UserService;

/**
 * Création du premier administrateur, avec les mêmes normalisations, validations et le même
 * hachage que les autres créations de comptes.
 */
@Service
public class AdminBootstrapService {

    public enum Outcome {
        /** Le compte ADMIN a été créé. */
        CREATED,
        /** Un ADMIN existait déjà : aucun compte n'a été modifié. */
        ADMIN_ALREADY_EXISTS
    }

    /** Résultat de l'initialisation ; {@code admin} est {@code null} si rien n'a été créé. */
    public record Result(Outcome outcome, User admin) {
    }

    private final UserRepository userRepository;
    private final UserService userService;
    private final Validator validator;

    public AdminBootstrapService(UserRepository userRepository, UserService userService, Validator validator) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.validator = validator;
    }

    /**
     * @throws BootstrapException si les valeurs sont invalides, ou si aucun ADMIN n'existe mais que
     *                            l'email est déjà pris (le compte existant n'est ni promu ni modifié)
     */
    @Transactional
    public Result initializeFirstAdmin(String name, String email, String rawPassword) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            return new Result(Outcome.ADMIN_ALREADY_EXISTS, null);
        }

        CreateUserRequest request = new CreateUserRequest(name, email, rawPassword, Role.ADMIN);
        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            // Seuls le champ et la règle sont cités, jamais la valeur reçue.
            String details = violations.stream()
                    .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                    .map(violation -> violation.getPropertyPath() + " : " + violation.getMessage())
                    .collect(Collectors.joining(" ; "));
            throw new BootstrapException("valeurs BOOTSTRAP_ADMIN_* invalides (" + details + ")");
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new BootstrapException("l'email " + request.email() + " est déjà utilisé par un compte existant. "
                    + "Aucun compte n'a été promu ni modifié ; choisissez un autre BOOTSTRAP_ADMIN_EMAIL");
        }

        User admin = userService.createAccount(request.name(), request.email(), request.password(), Role.ADMIN);
        return new Result(Outcome.CREATED, admin);
    }
}
