package com.example.user.service;

import com.example.user.model.User;
import com.example.user.repository.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JpaUserDetailsService implements UserDetailsService {
    private final UserRepository repo;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = repo.findByEmail(username).orElseThrow(() -> new UsernameNotFoundException("User not found"));
        GrantedAuthority auth = new SimpleGrantedAuthority("ROLE_" + user.getRole());
        return new org.springframework.security.core.userdetails.User(user.getEmail(), user.getPassword(), List.of(auth));
    }
}
