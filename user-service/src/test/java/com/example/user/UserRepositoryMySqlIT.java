package com.example.user;

import com.example.user.model.User;
import com.example.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class UserRepositoryMySqlIT {
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("testdb").withUsername("test").withPassword("test");

    static {
        mysql.start();
    }

    @Autowired
    UserRepository repo;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl);
        r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
    }

    @Test
    void saveAndFindInMySql() {
        User u = new User();
        u.setEmail("it@x.com");
        u.setPassword("pass");
        User saved = repo.save(u);
        Optional<User> found = repo.findByEmail("it@x.com");
        assertTrue(found.isPresent());
        assertEquals(saved.getEmail(), found.get().getEmail());
    }
}
