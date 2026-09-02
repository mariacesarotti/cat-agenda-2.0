package com.catagenda.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    // Real BCrypt encoder rather than a mock: the test that matters here is
    // "the stored hash isn't the plaintext password," which only means
    // something if the hashing is real.
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("cat@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register("cat@example.com", "supersecret123"))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void registerRejectsWeakPassword() {
        when(userRepository.existsByEmail(any())).thenReturn(false);

        assertThatThrownBy(() -> userService.register("cat@example.com", "short"))
                .isInstanceOf(WeakPasswordException.class);
    }

    @Test
    void registerHashesPasswordRatherThanStoringPlaintext() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.register("cat@example.com", "supersecret123");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User saved = captor.getValue();
        assertThat(saved.getPasswordHash()).isNotEqualTo("supersecret123");
        assertThat(passwordEncoder.matches("supersecret123", saved.getPasswordHash())).isTrue();
    }
}
