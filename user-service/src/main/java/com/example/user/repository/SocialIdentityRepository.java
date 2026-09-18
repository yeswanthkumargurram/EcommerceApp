package com.example.user.repository;

import com.example.user.model.SocialIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SocialIdentityRepository extends JpaRepository<SocialIdentity, Long> {
    Optional<SocialIdentity> findByProviderAndProviderUserId(String provider, String providerUserId);
}
