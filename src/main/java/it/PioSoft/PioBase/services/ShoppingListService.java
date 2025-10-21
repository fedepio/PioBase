package it.PioSoft.PioBase.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.PioSoft.PioBase.model.ShoppingItem;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service per la gestione della lista della spesa condivisa
 */
@Service
public class ShoppingListService {

    private static final String JSON_FILE_PATH = "config/shopping-list.json";
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    public ShoppingListService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Ottiene tutti gli item della lista della spesa
     */
    public List<ShoppingItem> getAllItems() {
        try {
            File file = new File(JSON_FILE_PATH);
            if (!file.exists()) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(file, new TypeReference<List<ShoppingItem>>() {});
        } catch (IOException e) {
            System.err.println("Errore lettura JSON: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Ottiene solo gli item non spuntati
     */
    public List<ShoppingItem> getActiveItems() {
        return getAllItems().stream()
                .filter(item -> !item.isSpuntato())
                .collect(Collectors.toList());
    }

    /**
     * Salva la lista sul file JSON
     */
    private void saveToFile(List<ShoppingItem> items) {
        try {
            File file = new File(JSON_FILE_PATH);
            file.getParentFile().mkdirs();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, items);
        } catch (IOException e) {
            System.err.println("Errore scrittura JSON: " + e.getMessage());
        }
    }

    /**
     * Aggiunge un nuovo item alla lista
     */
    public ShoppingItem addItem(ShoppingItem item) {
        List<ShoppingItem> items = getAllItems();
        items.add(item);
        saveToFile(items);

        // Notifica gli altri client
        messagingTemplate.convertAndSend("/topic/shopping-list", item);

        return item;
    }

    /**
     * Aggiorna un item esistente
     */
    public Optional<ShoppingItem> updateItem(String id, ShoppingItem updatedItem) {
        List<ShoppingItem> items = getAllItems();

        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getId().equals(id)) {
                updatedItem.setId(id);
                items.set(i, updatedItem);
                saveToFile(items);

                // Notifica gli altri client
                messagingTemplate.convertAndSend("/topic/shopping-list", updatedItem);

                return Optional.of(updatedItem);
            }
        }
        return Optional.empty();
    }

    /**
     * Rimuove un item dalla lista
     */
    public boolean removeItem(String id) {
        List<ShoppingItem> items = getAllItems();
        boolean removed = items.removeIf(item -> item.getId().equals(id));

        if (removed) {
            saveToFile(items);

            // Notifica gli altri client
            messagingTemplate.convertAndSend("/topic/shopping-list-delete", id);
        }

        return removed;
    }

    /**
     * Spunta/Despunta un item
     */
    public Optional<ShoppingItem> toggleItem(String id) {
        List<ShoppingItem> items = getAllItems();

        for (ShoppingItem item : items) {
            if (item.getId().equals(id)) {
                item.setSpuntato(!item.isSpuntato());
                saveToFile(items);

                // Notifica gli altri client
                messagingTemplate.convertAndSend("/topic/shopping-list", item);

                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    /**
     * Aggiunge un +1 a un item
     */
    public Optional<ShoppingItem> addPlusOne(String id, String username) {
        List<ShoppingItem> items = getAllItems();

        for (ShoppingItem item : items) {
            if (item.getId().equals(id)) {
                item.addPlusOne(username);
                saveToFile(items);

                // Notifica gli altri client
                messagingTemplate.convertAndSend("/topic/shopping-list", item);

                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    /**
     * Rimuove un +1 da un item
     */
    public Optional<ShoppingItem> removePlusOne(String id, String username) {
        List<ShoppingItem> items = getAllItems();

        for (ShoppingItem item : items) {
            if (item.getId().equals(id)) {
                item.removePlusOne(username);
                saveToFile(items);

                // Notifica gli altri client
                messagingTemplate.convertAndSend("/topic/shopping-list", item);

                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    /**
     * Ottiene un singolo item per ID
     */
    public Optional<ShoppingItem> getItemById(String id) {
        return getAllItems().stream()
                .filter(item -> item.getId().equals(id))
                .findFirst();
    }
}
