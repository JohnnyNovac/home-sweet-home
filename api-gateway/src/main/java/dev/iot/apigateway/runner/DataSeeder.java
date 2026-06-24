package dev.iot.apigateway.runner;

import dev.iot.apigateway.config.AdminUserProperties;
import dev.iot.apigateway.model.Role;
import dev.iot.apigateway.model.User;
import dev.iot.apigateway.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class DataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final AdminUserProperties adminUserProperties;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository, AdminUserProperties adminUserProperties, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.adminUserProperties = adminUserProperties;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (userRepository.count() == 0) {
            userRepository.save(new User(
                    adminUserProperties.username(),
                    passwordEncoder.encode(adminUserProperties.password()),
                    Set.of(Role.ADMIN),
                    true)
            );
        }
    }
}
