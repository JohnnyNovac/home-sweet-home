package dev.iot.apigateway.mapper;

import dev.iot.apigateway.dto.CreateUserDto;
import dev.iot.apigateway.model.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public User toUser(CreateUserDto createUserDto) {
        return new User(
                createUserDto.username(),
                createUserDto.roles(),
                createUserDto.enabled()
        );
    }

}
