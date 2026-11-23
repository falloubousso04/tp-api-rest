package sn.ucak.tpapirest.mapper;

import sn.ucak.tpapirest.dto.user.UserResponse;
import sn.ucak.tpapirest.entity.User;

/** Conversion entité → DTO : le hachage du mot de passe n'est jamais recopié. */
public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
