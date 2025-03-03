package com.omp.user.dto;

import com.omp.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class GetUserResponse {
    @Email
    @NotBlank
    private final String email;
    @NotBlank
    private final String name;

    public static GetUserResponse to(User user) {
        return new GetUserResponse(user.getEmail(), user.getName());
    }
}
