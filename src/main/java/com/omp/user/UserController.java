package com.omp.user;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/user")
public class UserController {
    private final UserService userService;

    @GetMapping("/{id}")
    public User getUser(final @PathVariable Long id) {
        return userService.findUserBy(id);
    }

    @PostMapping
    public Long createUser(final @RequestBody CreateUserDto createUserDto) {
        return userService.saveUserBy(createUserDto);
    }
}
