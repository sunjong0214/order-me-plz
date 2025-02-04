package com.omp.user;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateUserRequest {
    @NotBlank
    private final String email;
    @NotBlank
    private final String name;

    public static CreateUserDto of(final CreateUserRequest request) {
        return new CreateUserDto(request.email, request.name);
    }
}
