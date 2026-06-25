package dev.iot.apigateway.dto;

import dev.iot.apigateway.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record CreateUserDto(
        @NotBlank
        String username,

        @NotBlank
        String password,

        @NotNull
        Set<Role> roles,

        boolean enabled
) {
}
