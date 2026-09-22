package com.example.user.web;

import com.example.user.model.PasswordResetToken;
import com.example.user.model.SocialIdentity;
import com.example.user.model.User;
import com.example.user.model.UserProfile;
import com.example.user.repository.PasswordResetTokenRepository;
import com.example.user.repository.SocialIdentityRepository;
import com.example.user.repository.UserProfileRepository;
import com.example.user.repository.UserRepository;
import com.example.user.service.EmailService;
import com.example.user.web.dto.AuthRequest;
import com.example.user.web.dto.AuthResponse;
import com.example.user.web.dto.ForgotPasswordRequest;
import com.example.user.web.dto.ResetPasswordRequest;
import com.example.user.web.dto.SocialLoginRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private final UserRepository repo;
    private final UserProfileRepository profileRepo;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authManager;
    private final com.example.common.security.JwtProvider jwtProvider;
    private final PasswordResetTokenRepository resetTokenRepo;
    private final SocialIdentityRepository socialIdentityRepo;
    private final EmailService emailService;

    @Value("${app.password-reset.link-base:http://localhost:8080/reset-password?token=}")
    private String resetLinkBase;

    @Value("${app.password-reset.expiry-minutes:30}")
    private long resetTokenExpiryMinutes;

    @PostMapping("/register")
    @Transactional
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

        UserProfile profile = new UserProfile();
        profile.setUserId(u.getId());
        profile.setEmail(u.getEmail());
        profileRepo.save(profile);

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
        User user = repo.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "invalid email or password"));
        String token = jwtProvider.generateToken(user.getEmail(), Map.of("userId", user.getId(), "role", user.getRole()));
        log.info("User authenticated: email={}", request.getEmail());
        return ResponseEntity.ok(new AuthResponse(token));
    }

    @GetMapping("/me")
    public Map<String, String> me(Authentication authentication) {
        log.debug("Authenticated user details requested: email={}", authentication.getName());
        return Map.of("email", authentication.getName());
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        log.debug("Password reset requested for email={}", request.getEmail());
        // Always return 200 regardless of whether the email exists, to avoid leaking account existence.
        repo.findByEmail(request.getEmail()).ifPresent(user -> {
            String token = UUID.randomUUID().toString();
            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setUserId(user.getId());
            resetToken.setToken(token);
            resetToken.setExpiryDate(Instant.now().plus(resetTokenExpiryMinutes, ChronoUnit.MINUTES));
            resetTokenRepo.save(resetToken);
            emailService.sendPasswordResetEmail(user.getEmail(), resetLinkBase + token);
            log.info("Password reset token issued: userId={}", user.getId());
        });
        return ResponseEntity.ok(Map.of("message", "If that email is registered, a reset link has been sent"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        PasswordResetToken resetToken = resetTokenRepo.findByToken(request.getToken())
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "invalid or expired token"));
        if (resetToken.isUsed() || resetToken.isExpired()) {
            log.warn("Password reset rejected: userId={}, used={}, expired={}", resetToken.getUserId(), resetToken.isUsed(), resetToken.isExpired());
            throw new ResponseStatusException(BAD_REQUEST, "invalid or expired token");
        }
        User user = repo.findById(resetToken.getUserId())
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "invalid or expired token"));
        user.setPassword(encoder.encode(request.getNewPassword()));
        repo.save(user);
        resetToken.setUsed(true);
        resetTokenRepo.save(resetToken);
        log.info("Password reset completed: userId={}", user.getId());
        return ResponseEntity.ok(Map.of("message", "password has been reset"));
    }

    @PostMapping("/social/{provider}")
    @Transactional
    public ResponseEntity<AuthResponse> socialLogin(@PathVariable String provider, @Valid @RequestBody SocialLoginRequest request) {
        log.debug("Social login requested: provider={}, email={}", provider, request.getEmail());
        SocialIdentity identity = socialIdentityRepo.findByProviderAndProviderUserId(provider, request.getProviderUserId())
                .orElse(null);

        User user;
        if (identity != null) {
            user = repo.findById(identity.getUserId())
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "linked account no longer exists"));
        } else {
            // First time we see this social profile: link to an existing account by email, or auto-register.
            boolean isNewUser = repo.findByEmail(request.getEmail()).isEmpty();
            user = repo.findByEmail(request.getEmail()).orElseGet(() -> {
                User newUser = new User();
                newUser.setEmail(request.getEmail());
                newUser.setPassword(encoder.encode(UUID.randomUUID().toString()));
                return repo.save(newUser);
            });
            if (isNewUser) {
                UserProfile profile = new UserProfile();
                profile.setUserId(user.getId());
                profile.setEmail(user.getEmail());
                profile.setFirstName(request.getDisplayName());
                profileRepo.save(profile);
            }
            SocialIdentity newIdentity = new SocialIdentity();
            newIdentity.setUserId(user.getId());
            newIdentity.setProvider(provider);
            newIdentity.setProviderUserId(request.getProviderUserId());
            socialIdentityRepo.save(newIdentity);
            log.info("Social identity linked: userId={}, provider={}", user.getId(), provider);
        }

        String token = jwtProvider.generateToken(user.getEmail(), Map.of("userId", user.getId(), "role", user.getRole()));
        return ResponseEntity.ok(new AuthResponse(token));
    }
}
