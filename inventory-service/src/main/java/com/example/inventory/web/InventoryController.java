package com.example.inventory.web;

import com.example.inventory.model.Inventory;
import com.example.inventory.repository.InventoryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {
    private final InventoryRepository repo;

    @GetMapping
    public List<Inventory> list() { return repo.findAll(); }

    @PostMapping
    public ResponseEntity<Inventory> add(@RequestBody Inventory inv) {
        Inventory saved = repo.save(inv);
        return ResponseEntity.ok(saved);
    }
}
