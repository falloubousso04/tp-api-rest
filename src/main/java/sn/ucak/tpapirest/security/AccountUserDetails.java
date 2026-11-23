package sn.ucak.tpapirest.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import sn.ucak.tpapirest.entity.User;

/**
 * Adaptateur du compte pour la vérification du mot de passe à la connexion.
 * Le hachage est effacé par Spring Security dès l'authentification terminée.
 */
public class AccountUserDetails implements UserDetails, CredentialsContainer {

    private final Long id;
    private final String email;
    private final List<GrantedAuthority> authorities;
    private String passwordHash;

    public AccountUserDetails(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.authorities = List.of(new SimpleGrantedAuthority(user.getRole().authority()));
    }

    public Long getId() {
        return id;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }

    @Override
    public String toString() {
        return "AccountUserDetails[id=" + id + ", email=" + email + "]";
    }
}
