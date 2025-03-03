package com.omp.user;

import com.omp.user.dto.CreateUserDto;
import com.omp.user.dto.GetUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class UserService {
    private final UserRepository userRepository;

    public GetUserResponse findUserBy(final Long id) {
        return GetUserResponse.to(userRepository.findById(id).orElseThrow());
    }

    public Long saveUserBy(final CreateUserDto createUserDto) {
        return userRepository.save(CreateUserDto.from(createUserDto)).getId();
    }
}
