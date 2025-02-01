package com.omp.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class UserService {
    private final UserRepository userRepository;

    public User findUserBy(Long id) {
        return userRepository.findById(id).orElseThrow();
    }

    public Long saveUserBy(CreateUserDto createUserDto) {
        return userRepository.save(CreateUserDto.of(createUserDto)).getId();
    }
}
