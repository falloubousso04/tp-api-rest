package sn.ucak.tpapirest.entity;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Compte utilisateur. L'identifiant est généré par la base ; le mot de passe n'est
 * conservé que sous forme de hachage BCrypt.
 */
@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(name = User.EMAIL_UNIQUE_CONSTRAINT, columnNames = "email"),
        check = @CheckConstraint(name = "ck_users_email_normalized", constraint = "email = lower(btrim(email))"))
public class User {

    /** Nom de la contrainte d'unicité, utilisé pour identifier précisément un conflit d'email. */
    public static final String EMAIL_UNIQUE_CONSTRAINT = "uk_users_email";

    public static final int NAME_MAX_LENGTH = 100;
    public static final int EMAIL_MAX_LENGTH = 254;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(name = "email", nullable = false, length = EMAIL_MAX_LENGTH)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    protected User() {
        // requis par JPA
    }

    public User(String name, String email, String passwordHash, Role role) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    /** Met à jour les informations du profil ; le mot de passe est modifié séparément. */
    public void updateProfile(String name, String email, Role role) {
        this.name = name;
        this.email = email;
        this.role = role;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    @Override
    public String toString() {
        // Le hachage du mot de passe n'apparaît jamais dans les journaux.
        return "User{id=" + id + ", email='" + email + "', role=" + role + '}';
    }
}
