package com.example.user.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "social_identities", uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "providerUserId"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SocialIdentity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String providerUserId;
}
