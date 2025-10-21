package it.PioSoft.PioBase.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Rappresenta un item della lista della spesa condivisa
 */
public class ShoppingItem {

    private String id;
    private String nome;           // Chi ha messo il prodotto
    private String prodotto;       // Descrizione del prodotto (stringa libera)
    private int qta;               // Quantità
    private String urgenza;        // "s" (alta) o "n" (normale)
    private LocalDateTime data;    // Data di inserimento
    private List<String> plusOne;  // Lista delle persone che hanno fatto +1
    private boolean spuntato;      // Se il prodotto è stato acquistato

    public ShoppingItem() {
        this.id = UUID.randomUUID().toString();
        this.data = LocalDateTime.now();
        this.plusOne = new ArrayList<>();
        this.urgenza = "n";
        this.spuntato = false;
    }

    public ShoppingItem(String nome, String prodotto, int qta) {
        this();
        this.nome = nome;
        this.prodotto = prodotto;
        this.qta = qta;
    }

    /**
     * Aggiunge un +1 da parte di un utente
     * Se raggiunge 3 +1, l'urgenza diventa alta ("s")
     */
    public void addPlusOne(String username) {
        if (!plusOne.contains(username)) {
            plusOne.add(username);
            if (plusOne.size() >= 3) {
                this.urgenza = "s";
            }
        }
    }

    /**
     * Rimuove un +1
     */
    public void removePlusOne(String username) {
        plusOne.remove(username);
        // Se scende sotto i 3 +1, l'urgenza torna normale
        if (plusOne.size() < 3 && "s".equals(urgenza)) {
            this.urgenza = "n";
        }
    }

    // Getters e Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getProdotto() {
        return prodotto;
    }

    public void setProdotto(String prodotto) {
        this.prodotto = prodotto;
    }

    public int getQta() {
        return qta;
    }

    public void setQta(int qta) {
        this.qta = qta;
    }

    public String getUrgenza() {
        return urgenza;
    }

    public void setUrgenza(String urgenza) {
        this.urgenza = urgenza;
    }

    public LocalDateTime getData() {
        return data;
    }

    public void setData(LocalDateTime data) {
        this.data = data;
    }

    public List<String> getPlusOne() {
        return plusOne;
    }

    public void setPlusOne(List<String> plusOne) {
        this.plusOne = plusOne;
    }

    public boolean isSpuntato() {
        return spuntato;
    }

    public void setSpuntato(boolean spuntato) {
        this.spuntato = spuntato;
    }
}
