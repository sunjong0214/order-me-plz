package com.omp.user;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateUserDto {
    @NotBlank
    private final String email;
    @NotBlank
    private final String name;

    public static User from(final CreateUserDto dto) {
        return new User(dto.email, dto.name);
    }
}
