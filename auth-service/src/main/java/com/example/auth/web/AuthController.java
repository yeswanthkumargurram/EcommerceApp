package com.example.auth.web;

import com.example.auth.model.User;
import com.example.auth.repository.UserRepository;
import com.example.auth.web.dto.AuthRequest;
import com.example.auth.web.dto.AuthResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.CONFLICT;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private final UserRepository repo;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authManager;
    private final com.example.common.security.JwtProvider jwtProvider;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody AuthRequest request) {
        log.debug("Registration requested for email={}", request.getEmail());
        if (repo.findByEmail(request.getEmail()).isPresent()) {
            log.warn("Registration rejected because email is already registered: email={}", request.getEmail());
            throw new ResponseStatusException(CONFLICT, "email is already registered");
        }
        User u = new User();
        u.setEmail(request.getEmail());
        u.setPassword(encoder.encode(request.getPassword()));
        repo.save(u);
        log.info("User registered: userId={}, email={}", u.getId(), u.getEmail());
        return ResponseEntity.ok(Map.of("id", u.getId(), "email", u.getEmail()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        log.debug("Login requested for email={}", request.getEmail());
        try {
            authManager.authenticate(new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        } catch (AuthenticationException exception) {
            log.warn("Login rejected for email={}", request.getEmail());
            throw exception;
        }
        String token = jwtProvider.generateToken(request.getEmail());
        log.info("User authenticated: email={}", request.getEmail());
        return ResponseEntity.ok(new AuthResponse(token));
    }

    @GetMapping("/me")
    public Map<String, String> me(Authentication authentication) {
        log.debug("Authenticated user details requested: email={}", authentication.getName());
        return Map.of("email", authentication.getName());
    }
}
