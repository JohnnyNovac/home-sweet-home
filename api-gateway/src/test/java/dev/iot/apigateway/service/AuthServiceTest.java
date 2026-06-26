package dev.iot.apigateway.service;

import dev.iot.apigateway.config.SecurityProperties;
import dev.iot.apigateway.dto.LoginRequest;
import dev.iot.apigateway.mapper.UserMapper;
import dev.iot.apigateway.model.Role;
import dev.iot.apigateway.model.User;
import dev.iot.apigateway.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtEncoder jwtEncoder;

    @Mock
    private Jwt jwt;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        SecurityProperties securityProperties = new SecurityProperties("secret", Duration.ofMinutes(15));
        authService = new AuthService(userRepository, userMapper, passwordEncoder, securityProperties, jwtEncoder);
    }

    @Test
    @DisplayName("Should return the encoded token when the username, password and enabled flag all check out")
    void shouldReturnTokenOnValidLogin() {
        User user = new User("admin", "stored-hash", Set.of(Role.ADMIN), true);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "stored-hash")).thenReturn(true);
        when(jwtEncoder.encode(any())).thenReturn(jwt);
        when(jwt.getTokenValue()).thenReturn("signed.jwt.token");

        String token = authService.login(new LoginRequest("admin", "password"));

        assertThat(token).isEqualTo("signed.jwt.token");
    }

    @Test
    @DisplayName("Should return 401 error when the username is unknown")
    void shouldReturn401OnUnknownUser() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "password")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should return 401 error when the password is incorrect")
    void shouldReturn401OnIncorrectPassword() {
        User user = new User("admin", "stored-hash", Set.of(Role.ADMIN), true);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "password")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should return 401 error when the user is disabled")
    void shouldReturn401OnDisabledUser() {
        User user = new User("admin", "stored-hash", Set.of(Role.ADMIN), false);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "stored-hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "password")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}