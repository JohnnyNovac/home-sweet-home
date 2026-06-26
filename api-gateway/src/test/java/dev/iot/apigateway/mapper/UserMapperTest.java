package dev.iot.apigateway.mapper;

import dev.iot.apigateway.dto.CreateUserDto;
import dev.iot.apigateway.model.Role;
import dev.iot.apigateway.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class UserMapperTest {

    private UserMapper userMapper;

    @BeforeEach
    void setUp() {
        userMapper = new UserMapper();
    }

    @Test
    @DisplayName("Should correctly map CreateUserDto to User")
    void shouldMapEventDtoToSensorData() {
        CreateUserDto createUserDto = new CreateUserDto("user", "password", Set.of(Role.ADMIN), true);

        User result = userMapper.toUser(createUserDto);

        assertThat(result.getUsername()).isEqualTo("user");
        assertThat(result.getPasswordHash()).isNull();
        assertThat(result.getRoles()).isEqualTo(Set.of(Role.ADMIN));
        assertThat(result.isEnabled()).isTrue();
    }
}
