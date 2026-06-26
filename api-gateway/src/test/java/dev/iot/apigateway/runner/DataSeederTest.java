package dev.iot.apigateway.runner;

import dev.iot.apigateway.config.AdminUserProperties;
import dev.iot.apigateway.model.Role;
import dev.iot.apigateway.model.User;
import dev.iot.apigateway.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    private DataSeeder dataSeeder;

    @BeforeEach
    void setUp() {
        AdminUserProperties adminUserProperties = new AdminUserProperties("admin", "s3cret");
        dataSeeder = new DataSeeder(userRepository, adminUserProperties, passwordEncoder);
    }

    @Test
    @DisplayName("Should seed an enabled admin with the hashed password when the collection is empty")
    void shouldSeedAdminWhenEmpty() throws Exception {
        when(userRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode("s3cret")).thenReturn("hashed-secret");

        dataSeeder.run(null);

        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getUsername()).isEqualTo("admin");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed-secret");
        assertThat(saved.getRoles()).containsExactly(Role.ADMIN);
        assertThat(saved.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Should not seed or hash anything when the collection already holds users")
    void shouldNotSeedWhenNotEmpty() throws Exception {
        when(userRepository.count()).thenReturn(1L);

        dataSeeder.run(null);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }
}