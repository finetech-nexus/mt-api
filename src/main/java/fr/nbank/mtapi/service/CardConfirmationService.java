package fr.nbank.mtapi.service;

import fr.nbank.mtapi.domain.CardConfirmation;
import fr.nbank.mtapi.repo.CardConfirmationRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CardConfirmationService {
    private final CardConfirmationRepository confirmations;
    private final ChannelClient channel;

    public CardConfirmationService(CardConfirmationRepository confirmations, ChannelClient channel) {
        this.confirmations = confirmations;
        this.channel = channel;
    }

    public Map<String, Object> open(Map<String, Object> body) {
        String transactionId = text(body.get("transactionId"));
        if (transactionId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "transactionId is required");
        }
        CardConfirmation row = confirmations.findByTransactionId(transactionId).orElseGet(CardConfirmation::new);
        row.setTransactionId(transactionId);
        row.setClientReference(text(body.get("clientReference")));
        row.setUserId(text(body.get("userId")));
        row.setTenantId(text(body.get("tenantId")));
        row.setAccountId(text(body.get("accountId")));
        row.setAmount(text(body.get("amount")));
        row.setCurrency(text(body.getOrDefault("currency", "TND")));
        row.setMerchant(text(body.get("merchant")));
        row.setStatus("PENDING");
        if (row.getCreatedAt() == null) {
            row.setCreatedAt(Instant.now());
        }
        confirmations.save(row);
        return view(row);
    }

    public List<Map<String, Object>> pending(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        return confirmations.findByUserIdAndStatusOrderByCreatedAtDesc(userId, "PENDING").stream().map(this::view).toList();
    }

    public Map<String, Object> decide(String transactionId, boolean accepted, String userId) {
        CardConfirmation row = confirmations.findByTransactionId(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Confirmation not found"));
        if (userId != null && row.getUserId() != null && !row.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This payment belongs to another customer");
        }
        if (!"PENDING".equals(row.getStatus())) {
            return view(row);
        }
        row.setStatus(accepted ? "ACCEPTED" : "REJECTED");
        confirmations.save(row);
        channel.publishDecision(transactionId, accepted);
        return view(row);
    }

    public Map<String, Object> expire(String transactionId) {
        CardConfirmation row = confirmations.findByTransactionId(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Confirmation not found"));
        if ("PENDING".equals(row.getStatus())) {
            row.setStatus("EXPIRED");
            confirmations.save(row);
        }
        return view(row);
    }

    private Map<String, Object> view(CardConfirmation row) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("transactionId", row.getTransactionId());
        body.put("clientReference", row.getClientReference());
        body.put("userId", row.getUserId());
        body.put("tenantId", row.getTenantId());
        body.put("accountId", row.getAccountId());
        body.put("amount", row.getAmount());
        body.put("currency", row.getCurrency());
        body.put("merchant", row.getMerchant());
        body.put("status", row.getStatus());
        body.put("createdAt", row.getCreatedAt());
        return body;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
