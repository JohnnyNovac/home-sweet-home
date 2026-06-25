package dev.iot.apigateway.service;

import dev.iot.apigateway.config.SecurityProperties;
import dev.iot.apigateway.dto.CreateUserDto;
import dev.iot.apigateway.dto.LoginRequest;
import dev.iot.apigateway.mapper.UserMapper;
import dev.iot.apigateway.model.User;
import dev.iot.apigateway.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    // A format-valid bcrypt hash (strength 10, the BCryptPasswordEncoder default) used as a placeholder when the
    // username is unknown, so the password check runs the full bcrypt cost regardless and login time does not leak
    // whether the account exists.
    private static final String UNKNOWN_USER_HASH = "$2a$10$edLsR5WUmj3fSFGoC28VW.YvH.NqBc.mtGmAEvgP5JzuqUcHO7O5O";

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties securityProperties;
    private final JwtEncoder jwtEncoder;

    public AuthService(
            UserRepository userRepository,
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            SecurityProperties securityProperties,
            JwtEncoder jwtEncoder
    ) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.securityProperties = securityProperties;
        this.jwtEncoder = jwtEncoder;
    }

    public void createUser(CreateUserDto createUserDto) {
        User createUser = userMapper.toUser(createUserDto);
        createUser.setPasswordHash(passwordEncoder.encode(createUserDto.password()));
        User user = userRepository.save(createUser);
        logger.info("User successfully created with id {}", user.getId());
    }

    public String login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username()).orElse(null);
        String passwordHash = user != null ? user.getPasswordHash() : UNKNOWN_USER_HASH;

        if (!passwordEncoder.matches(request.password(), passwordHash) || user == null || !user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(now.plus(securityProperties.accessTtl()))
                .claim("roles", user.getRoles().stream().map(Enum::name).toList())
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
