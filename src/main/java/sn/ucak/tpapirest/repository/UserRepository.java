package sn.ucak.tpapirest.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import sn.ucak.tpapirest.entity.Role;
import sn.ucak.tpapirest.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    boolean existsByRole(Role role);
}
