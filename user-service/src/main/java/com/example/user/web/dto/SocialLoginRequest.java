package com.example.user.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Represents an already-verified social profile (e.g. validated client-side against the provider's SDK/tokeninfo endpoint). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SocialLoginRequest {
    @NotBlank(message = "providerUserId is required")
    private String providerUserId;

    @NotBlank(message = "email is required")
    @Email(message = "email must be valid")
    private String email;

    private String displayName;
}
