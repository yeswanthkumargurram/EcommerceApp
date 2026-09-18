package com.example.user.web;

import com.example.user.model.UserProfile;
import com.example.user.repository.UserProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserProfileController {
    private final UserProfileRepository repo;

    @GetMapping("/me")
    public ResponseEntity<UserProfile> getOwnProfile(Authentication authentication) {
        return repo.findByEmail(authentication.getName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfile> updateOwnProfile(Authentication authentication, @RequestBody UserProfile p) {
        return repo.findByEmail(authentication.getName())
                .map(existing -> {
                    existing.setFirstName(p.getFirstName());
                    existing.setLastName(p.getLastName());
                    repo.save(existing);
                    return ResponseEntity.ok(existing);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserProfile> get(@PathVariable Long id, Authentication authentication) {
        UserProfile profile = requireOwnedProfile(id, authentication);
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserProfile> update(@PathVariable Long id, @RequestBody UserProfile p, Authentication authentication) {
        UserProfile existing = requireOwnedProfile(id, authentication);
        existing.setFirstName(p.getFirstName());
        existing.setLastName(p.getLastName());
        repo.save(existing);
        return ResponseEntity.ok(existing);
    }

    // Enforces that a caller can only access their own profile (matched by JWT subject email), preventing IDOR.
    private UserProfile requireOwnedProfile(Long id, Authentication authentication) {
        UserProfile profile = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!profile.getEmail().equalsIgnoreCase(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "cannot access another user's profile");
        }
        return profile;
    }
}

