package com.example.product;

import com.example.product.model.Product;
import com.example.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class ProductRepositoryTest {

    @Autowired
    ProductRepository repo;

    @Test
    void saveAndFind() {
        Product p = new Product();
        p.setName("T-shirt");
        p.setDescription("A comfy tee");
        p.setPrice(19.99);
        Product saved = repo.save(p);
        Optional<Product> found = repo.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("T-shirt", found.get().getName());
    }
}
