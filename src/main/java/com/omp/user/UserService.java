package com.omp.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class UserService {
    private final UserRepository userRepository;

    public User findUserBy(final Long id) {
        return userRepository.findById(id).orElseThrow();
    }

    public Long saveUserBy(final CreateUserDto createUserDto) {
        return userRepository.save(CreateUserDto.of(createUserDto)).getId();
    }
}
