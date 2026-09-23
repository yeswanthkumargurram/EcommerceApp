package com.example.product.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

// Ignore Hibernate lazy-proxy internals so Jackson doesn't try to serialize them when Product.category is an uninitialized proxy
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Data
public class Category extends BaseModel {
    private String name;

    private String description;

    // JsonIgnore prevents infinite recursion with Product.category during serialization
    @JsonIgnore
    @OneToMany(mappedBy = "category", fetch = FetchType.LAZY)
    private List<Product> products = new ArrayList<>();
}
