package it.PioSoft.PioBase.controller;

import it.PioSoft.PioBase.model.ShoppingItem;
import it.PioSoft.PioBase.services.ShoppingListService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller REST per la gestione della lista della spesa condivisa "Pio Smart Spesa"
 */
@RestController
@RequestMapping("/api/shopping-list")
@CrossOrigin(origins = "*")
public class ShoppingListController {

    private final ShoppingListService shoppingListService;

    public ShoppingListController(ShoppingListService shoppingListService) {
        this.shoppingListService = shoppingListService;
    }

    /**
     * GET /api/shopping-list
     * Ottiene tutti gli item della lista
     */
    @GetMapping
    public ResponseEntity<List<ShoppingItem>> getAllItems() {
        return ResponseEntity.ok(shoppingListService.getAllItems());
    }

    /**
     * GET /api/shopping-list/active
     * Ottiene solo gli item non spuntati
     */
    @GetMapping("/active")
    public ResponseEntity<List<ShoppingItem>> getActiveItems() {
        return ResponseEntity.ok(shoppingListService.getActiveItems());
    }

    /**
     * GET /api/shopping-list/{id}
     * Ottiene un singolo item per ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ShoppingItem> getItemById(@PathVariable String id) {
        return shoppingListService.getItemById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /api/shopping-list
     * Aggiunge un nuovo item alla lista
     */
    @PostMapping
    public ResponseEntity<ShoppingItem> addItem(@RequestBody ShoppingItem item) {
        ShoppingItem created = shoppingListService.addItem(item);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * PUT /api/shopping-list/{id}
     * Aggiorna un item esistente
     */
    @PutMapping("/{id}")
    public ResponseEntity<ShoppingItem> updateItem(
            @PathVariable String id,
            @RequestBody ShoppingItem item) {
        return shoppingListService.updateItem(id, item)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * DELETE /api/shopping-list/{id}
     * Rimuove un item dalla lista
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable String id) {
        if (shoppingListService.removeItem(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * PUT /api/shopping-list/{id}/toggle
     * Spunta/Despunta un item
     */
    @PutMapping("/{id}/toggle")
    public ResponseEntity<ShoppingItem> toggleItem(@PathVariable String id) {
        return shoppingListService.toggleItem(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * PUT /api/shopping-list/{id}/plusone
     * Aggiunge un +1 a un item
     * Body: { "username": "Mario" }
     */
    @PutMapping("/{id}/plusone")
    public ResponseEntity<ShoppingItem> addPlusOne(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        String username = body.get("username");
        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        return shoppingListService.addPlusOne(id, username)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * DELETE /api/shopping-list/{id}/plusone
     * Rimuove un +1 da un item
     * Body: { "username": "Mario" }
     */
    @DeleteMapping("/{id}/plusone")
    public ResponseEntity<ShoppingItem> removePlusOne(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        String username = body.get("username");
        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        return shoppingListService.removePlusOne(id, username)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
