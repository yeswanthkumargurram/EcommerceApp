package com.example.auth;

import com.example.auth.model.User;
import com.example.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class UserRepositoryTest {
    @Autowired
    UserRepository repo;

    @Test
    void saveAndFind() {
        User u = new User();
        u.setEmail("it@x.com");
        u.setPassword("pass");
        User saved = repo.save(u);
        Optional<User> found = repo.findByEmail("it@x.com");
        assertTrue(found.isPresent());
        assertEquals(saved.getEmail(), found.get().getEmail());
    }
}
