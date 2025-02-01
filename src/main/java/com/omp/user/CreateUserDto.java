package com.omp.user;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateUserDto {
    private final String email;
    private final String password;
    private final String name;

    public static User of(CreateUserDto dto) {
        return new User(dto.email, dto.password, dto.name);
    }
}
