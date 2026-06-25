package dev.iot.apigateway.controller;

import dev.iot.apigateway.dto.LoginRequest;
import dev.iot.apigateway.dto.LoginResponse;
import dev.iot.apigateway.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody @Valid LoginRequest loginRequest) {
        String token = authService.login(loginRequest);
        return new LoginResponse(token);
    }
}
