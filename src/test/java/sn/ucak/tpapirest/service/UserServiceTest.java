package sn.ucak.tpapirest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import sn.ucak.tpapirest.dto.auth.RegisterRequest;
import sn.ucak.tpapirest.dto.user.UpdateUserRequest;
import sn.ucak.tpapirest.dto.user.UserResponse;
import sn.ucak.tpapirest.entity.Role;
import sn.ucak.tpapirest.entity.User;
import sn.ucak.tpapirest.exception.EmailAlreadyUsedException;
import sn.ucak.tpapirest.exception.ResourceNotFoundException;
import sn.ucak.tpapirest.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String EXISTING_HASH = "$2a$10$hachageexistantfictif";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private static User existingUser() {
        return new User("Awa Diop", "awa@exemple.test", EXISTING_HASH, Role.USER);
    }

    @Test
    void registerAssignsUserRoleAndHashesPasswordOnce() {
        when(passwordEncoder.encode("Motdepasse-1")).thenReturn("$2a$10$nouveauhachage");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.register(new RegisterRequest(" Awa ", " AWA@Exemple.test ", "Motdepasse-1"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.USER);
        assertThat(saved.getValue().getEmail()).isEqualTo("awa@exemple.test");
        assertThat(saved.getValue().getName()).isEqualTo("Awa");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("$2a$10$nouveauhachage");
        verify(passwordEncoder, times(1)).encode(any());
        assertThat(response.role()).isEqualTo(Role.USER);
    }

    @Test
    void registerRefusesEmailAlreadyUsedWithoutSaving() {
        when(userRepository.existsByEmail("awa@exemple.test")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(new RegisterRequest("Awa", "AWA@exemple.test", "Motdepasse-1")))
                .isInstanceOf(EmailAlreadyUsedException.class);
        verify(userRepository, never()).saveAndFlush(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void updateWithoutPasswordKeepsExistingHash() {
        User user = existingUser();
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(user)).thenReturn(user);

        userService.update(2L, new UpdateUserRequest("Awa Ndiaye", "awa@exemple.test", Role.ADMIN, null));

        assertThat(user.getPasswordHash()).isEqualTo(EXISTING_HASH);
        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void updateWithPasswordHashesTheNewValue() {
        User user = existingUser();
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(user)).thenReturn(user);
        when(passwordEncoder.encode("Nouveau-mdp-1")).thenReturn("$2a$10$hachagenouveau");

        userService.update(2L, new UpdateUserRequest("Awa", "awa@exemple.test", Role.USER, "Nouveau-mdp-1"));

        assertThat(user.getPasswordHash()).isEqualTo("$2a$10$hachagenouveau");
    }

    @Test
    void updateAllowsKeepingOwnEmailButRefusesAnotherAccountEmail() {
        User user = existingUser();
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndIdNot("autre@exemple.test", 2L)).thenReturn(true);

        assertThatThrownBy(() -> userService.update(2L,
                new UpdateUserRequest("Awa", "autre@exemple.test", Role.USER, null)))
                .isInstanceOf(EmailAlreadyUsedException.class);
        assertThat(user.getEmail()).isEqualTo("awa@exemple.test");
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateOfMissingUserIsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.update(99L, new UpdateUserRequest("Awa", "awa@exemple.test", Role.USER, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void translatesOnlyTheEmailUniqueConstraintIntoAConflict() {
        DataIntegrityViolationException emailViolation = new DataIntegrityViolationException("doublon",
                new ConstraintViolationException("doublon", new SQLException("23505"), User.EMAIL_UNIQUE_CONSTRAINT));
        DataIntegrityViolationException otherViolation = new DataIntegrityViolationException("autre",
                new ConstraintViolationException("autre", new SQLException("23514"), "ck_users_email_normalized"));

        assertThat(UserService.isEmailUniqueViolation(emailViolation)).isTrue();
        assertThat(UserService.isEmailUniqueViolation(otherViolation)).isFalse();
    }

    @Test
    void concurrentEmailDuplicateDetectedByDatabaseBecomesConflict() {
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$hachage");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("doublon",
                new ConstraintViolationException("doublon", new SQLException("23505"), User.EMAIL_UNIQUE_CONSTRAINT)));

        assertThatThrownBy(() -> userService.register(new RegisterRequest("Awa", "awa@exemple.test", "Motdepasse-1")))
                .isInstanceOf(EmailAlreadyUsedException.class);
    }

    @Test
    void otherDatabaseErrorsAreNotReportedAsEmailConflicts() {
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$hachage");
        DataIntegrityViolationException other = new DataIntegrityViolationException("autre",
                new ConstraintViolationException("autre", new SQLException("23514"), "ck_users_role"));
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(other);

        assertThatThrownBy(() -> userService.register(new RegisterRequest("Awa", "awa@exemple.test", "Motdepasse-1")))
                .isSameAs(other);
    }
}
