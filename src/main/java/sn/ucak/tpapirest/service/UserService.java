package sn.ucak.tpapirest.service;

import java.util.List;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.ucak.tpapirest.dto.auth.RegisterRequest;
import sn.ucak.tpapirest.dto.user.CreateUserRequest;
import sn.ucak.tpapirest.dto.user.UpdateUserRequest;
import sn.ucak.tpapirest.dto.user.UserResponse;
import sn.ucak.tpapirest.entity.Role;
import sn.ucak.tpapirest.entity.User;
import sn.ucak.tpapirest.exception.EmailAlreadyUsedException;
import sn.ucak.tpapirest.exception.ResourceNotFoundException;
import sn.ucak.tpapirest.mapper.UserMapper;
import sn.ucak.tpapirest.repository.UserRepository;

/**
 * Règles métier des comptes utilisateurs.
 *
 * <p>Les données reçues sont déjà normalisées et validées (DTO + Bean Validation).
 * Le mot de passe validé est haché une seule fois avec BCrypt ; s'il est omis lors
 * d'une modification, le hachage existant est conservé tel quel.</p>
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserResponse> findAll() {
        return userRepository.findAll(Sort.by("id")).stream()
                .map(UserMapper::toResponse)
                .toList();
    }

    public UserResponse findById(Long id) {
        return UserMapper.toResponse(getUser(id));
    }

    /** Inscription publique : le rôle USER est attribué par le serveur. */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        return UserMapper.toResponse(createAccount(request.name(), request.email(), request.password(), Role.USER));
    }

    /** Création par un administrateur, avec le rôle choisi. */
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        return UserMapper.toResponse(createAccount(request.name(), request.email(), request.password(), request.role()));
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = getUser(id);
        // Le compte peut conserver son propre email ; celui d'un autre compte est refusé.
        if (userRepository.existsByEmailAndIdNot(request.getEmail(), id)) {
            throw new EmailAlreadyUsedException();
        }
        user.updateProfile(request.getName(), request.getEmail(), request.getRole());
        if (request.hasPassword()) {
            user.changePasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        return UserMapper.toResponse(saveAndFlush(user));
    }

    @Transactional
    public void delete(Long id) {
        userRepository.delete(getUser(id));
    }

    /**
     * Crée un compte après contrôle de l'unicité de l'email. Utilisé par l'inscription, la création
     * administrative et l'initialisation du premier administrateur.
     */
    @Transactional
    public User createAccount(String name, String email, String rawPassword, Role role) {
        // Contrôle préalable pour un message lisible ; la contrainte uk_users_email couvre
        // en plus les écritures concurrentes.
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyUsedException();
        }
        User user = new User(name, email, passwordEncoder.encode(rawPassword), role);
        return saveAndFlush(user);
    }

    private User getUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.user(id));
    }

    /**
     * Écrit immédiatement en base pour traduire une violation de la contrainte d'unicité de
     * l'email en conflit 409. Les autres violations de contraintes ne sont pas confondues avec
     * un conflit d'email : elles sont propagées telles quelles.
     */
    private User saveAndFlush(User user) {
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            if (isEmailUniqueViolation(exception)) {
                throw new EmailAlreadyUsedException();
            }
            throw exception;
        }
    }

    static boolean isEmailUniqueViolation(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConstraintViolationException violation) {
                return User.EMAIL_UNIQUE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName());
            }
            current = current.getCause();
        }
        return false;
    }
}
