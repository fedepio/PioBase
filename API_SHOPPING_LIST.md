# API Pio Smart Spesa

Documentazione delle API REST per la gestione della lista della spesa condivisa.

## Base URL
```
http://localhost:8080/api/shopping-list
```

## Endpoints

### 1. Ottenere tutti gli item
```
GET /api/shopping-list
```
**Response:** Array di ShoppingItem

---

### 2. Ottenere solo item attivi (non spuntati)
```
GET /api/shopping-list/active
```
**Response:** Array di ShoppingItem non spuntati

---

### 3. Ottenere un singolo item
```
GET /api/shopping-list/{id}
```
**Response:** ShoppingItem

---

### 4. Aggiungere un nuovo item
```
POST /api/shopping-list
Content-Type: application/json

{
  "nome": "Mario",
  "prodotto": "Latte intero",
  "qta": 2
}
```
**Response:** ShoppingItem creato (con id, data, urgenza="n", plusOne=[], spuntato=false)

---

### 5. Aggiornare un item
```
PUT /api/shopping-list/{id}
Content-Type: application/json

{
  "nome": "Mario",
  "prodotto": "Latte parzialmente scremato",
  "qta": 3,
  "urgenza": "n",
  "spuntato": false
}
```
**Response:** ShoppingItem aggiornato

---

### 6. Eliminare un item
```
DELETE /api/shopping-list/{id}
```
**Response:** 204 No Content

---

### 7. Spuntare/Despuntare un item
```
PUT /api/shopping-list/{id}/toggle
```
**Response:** ShoppingItem con spuntato invertito

---

### 8. Aggiungere +1 a un item
```
PUT /api/shopping-list/{id}/plusone
Content-Type: application/json

{
  "username": "Luigi"
}
```
**Response:** ShoppingItem con +1 aggiunto
**Nota:** Se raggiunge 3 +1, l'urgenza diventa automaticamente "s"

---

### 9. Rimuovere +1 da un item
```
DELETE /api/shopping-list/{id}/plusone
Content-Type: application/json

{
  "username": "Luigi"
}
```
**Response:** ShoppingItem con +1 rimosso

---

## Modello Dati: ShoppingItem

```json
{
  "id": "uuid-generato-automaticamente",
  "nome": "Nome di chi ha aggiunto il prodotto",
  "prodotto": "Descrizione del prodotto",
  "qta": 1,
  "urgenza": "n",  // "n" = normale, "s" = alta (>= 3 +1)
  "data": "2025-10-21T10:30:00",
  "plusOne": ["Mario", "Luigi"],
  "spuntato": false
}
```

---

## WebSocket - Notifiche Push

### Connessione WebSocket
```
ws://localhost:8080/ws-shopping
```

### Topic da sottoscrivere

1. **Aggiornamenti item:**
   ```
   /topic/shopping-list
   ```
   Ricevi aggiornamenti quando un item viene aggiunto, modificato, spuntato o riceve +1

2. **Cancellazioni item:**
   ```
   /topic/shopping-list-delete
   ```
   Ricevi l'ID dell'item quando viene eliminato

### Esempio di connessione (JavaScript con SockJS + STOMP)

```javascript
const socket = new SockJS('http://localhost:8080/ws-shopping');
const stompClient = Stomp.over(socket);

stompClient.connect({}, function(frame) {
  console.log('Connesso: ' + frame);

  // Sottoscrivi agli aggiornamenti
  stompClient.subscribe('/topic/shopping-list', function(message) {
    const item = JSON.parse(message.body);
    console.log('Item aggiornato:', item);
    // Aggiorna UI
  });

  // Sottoscrivi alle cancellazioni
  stompClient.subscribe('/topic/shopping-list-delete', function(message) {
    const itemId = message.body;
    console.log('Item cancellato:', itemId);
    // Rimuovi dalla UI
  });
});
```

---

## File di Persistenza

I dati vengono salvati in:
```
config/shopping-list.json
```

Il file viene creato automaticamente al primo inserimento.

---

## Note Implementative

- **CORS:** Abilitato per tutte le origini (`*`)
- **Formato Date:** ISO 8601 con LocalDateTime
- **ID:** UUID generato automaticamente
- **Thread-safety:** Il service usa file I/O sincrono (sufficiente per carichi leggeri)
- **Notifiche real-time:** Ogni operazione (add, update, delete, toggle, +1) invia notifiche via WebSocket
