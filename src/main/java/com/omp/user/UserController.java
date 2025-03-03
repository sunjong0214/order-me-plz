package com.omp.user;

import com.omp.user.dto.CreateUserRequest;
import com.omp.user.dto.GetUserResponse;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;
    private static final String USER_URI = "/api/v1/users/";

    @GetMapping("/{id}")
    public GetUserResponse getUser(final @PathVariable Long id) {
        return userService.findUserBy(id);
    }

    @PostMapping
    public ResponseEntity<Void> createUser(final @RequestBody CreateUserRequest request) {
        return ResponseEntity
                .created(URI.create(USER_URI + userService.saveUserBy(CreateUserRequest.from(request))))
                .build();
    }
}
