package fr.nbank.mtapi.service;

import fr.nbank.mtapi.domain.CardConfirmation;
import fr.nbank.mtapi.repo.CardConfirmationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CardConfirmationService {
    public static final Duration CONFIRMATION_WINDOW = Duration.ofMinutes(3);

    private final CardConfirmationRepository confirmations;
    private final MtPaymentService payments;

    public CardConfirmationService(CardConfirmationRepository confirmations, MtPaymentService payments) {
        this.confirmations = confirmations;
        this.payments = payments;
    }

    @Transactional
    public Map<String, Object> open(Map<String, Object> body) {
        String transactionId = text(body.get("transactionId"));
        String clientReference = text(body.get("clientReference"));
        if (transactionId == null && clientReference == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "transactionId is required");
        }
        CardConfirmation row = findExisting(transactionId, clientReference);
        if (row != null && "CONFIRMATION_REQUIRED".equals(stateOf(body)) && terminal(row.getStatus())) {
            return view(row);
        }
        if (row == null) {
            row = new CardConfirmation();
            row.setCreatedAt(Instant.now());
        }
        if (transactionId != null && row.getTransactionId() == null) {
            row.setTransactionId(transactionId);
        }
        if (transactionId != null && (row.getTransactionId() == null || row.getTransactionId().isBlank())) {
            row.setTransactionId(transactionId);
        }
        if (row.getTransactionId() == null) {
            row.setTransactionId(transactionId != null ? transactionId : clientReference);
        }
        row.setClientReference(first(clientReference, row.getClientReference()));
        row.setUserId(first(text(body.get("userId")), row.getUserId()));
        row.setTenantId(first(text(body.get("tenantId")), row.getTenantId()));
        row.setAccountId(first(text(body.get("accountId")), row.getAccountId()));
        row.setAmount(first(text(body.get("amount")), row.getAmount()));
        row.setCurrency(first(text(body.getOrDefault("currency", null)), row.getCurrency(), "TND"));
        row.setMerchant(first(text(body.get("merchant")), row.getMerchant()));
        row.setAvailableBalance(text(body.get("availableBalance")));
        String state = stateOf(body);
        row.setOperationState(state);
        row.setStatus(statusFor(state));
        if ("INSUFFICIENT_BALANCE".equals(state)) {
            row.setSeen(false);
        }
        if ("CONFIRMATION_REQUIRED".equals(state) && row.getExpiresAt() == null) {
            row.setExpiresAt(Instant.now().plus(CONFIRMATION_WINDOW));
        }
        confirmations.save(row);
        return view(row);
    }

    public Map<String, Object> list(String userId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("confirmations", pending(userId));
        body.put("notices", notices(userId));
        return body;
    }

    public List<Map<String, Object>> pending(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        return confirmations.findByUserIdAndStatusInOrderByCreatedAtDesc(userId, List.of("PENDING", "CONFIRMATION_REQUIRED"))
                .stream().filter(row -> !expired(row)).map(this::view).toList();
    }

    public List<Map<String, Object>> notices(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        return confirmations.findOpenNotices(userId).stream().map(this::view).toList();
    }

    public Map<String, Object> one(String transactionId) {
        return view(require(transactionId));
    }

    @Transactional
    public Map<String, Object> decide(String transactionId, boolean accepted, String userId) {
        CardConfirmation row = require(transactionId);
        if (userId != null && row.getUserId() != null && !row.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This payment belongs to another customer");
        }
        if (expired(row)) {
            row.setStatus("EXPIRED");
            row.setOperationState("CANCELLED");
            confirmations.save(row);
            return view(row);
        }
        if (!openForDecision(row.getStatus())) {
            return view(row);
        }
        row.setStatus(accepted ? "ACCEPTED" : "CANCELLED");
        row.setOperationState(accepted ? "CONFIRMED" : "CANCELLED");
        confirmations.save(row);
        try {
            payments.resumeAuthorization(row.getClientReference(), accepted);
        } catch (RuntimeException ex) {
            row.setStatus("CONFIRMATION_REQUIRED");
            row.setOperationState("CONFIRMATION_REQUIRED");
            confirmations.save(row);
            throw ex;
        }
        return view(row);
    }

    @Transactional
    public Map<String, Object> claim(Map<String, Object> body) {
        CardConfirmation row = findExisting(text(body.get("transactionId")), text(body.get("clientReference")));
        if (row == null) {
            return Map.of("claimed", true, "operationState", "FUNDS_BLOCKED");
        }
        if (!"ACCEPTED".equals(row.getStatus())) {
            return Map.of("claimed", false, "operationState", row.getOperationState() == null ? row.getStatus() : row.getOperationState());
        }
        row.setStatus("CLAIMED");
        row.setOperationState("CONFIRMED");
        confirmations.saveAndFlush(row);
        return Map.of("claimed", true, "operationState", "CONFIRMED");
    }

    @Transactional
    public Map<String, Object> markBlocked(Map<String, Object> body) {
        CardConfirmation row = findExisting(text(body.get("transactionId")), text(body.get("clientReference")));
        if (row == null) {
            return Map.of("operationState", "FUNDS_BLOCKED");
        }
        if (!"BLOCKED".equals(row.getStatus())) {
            row.setStatus("BLOCKED");
            row.setOperationState("FUNDS_BLOCKED");
            row.setAvailableBalance(first(text(body.get("availableBalance")), row.getAvailableBalance()));
            confirmations.save(row);
        }
        return view(row);
    }

    @Transactional
    public Map<String, Object> seen(String transactionId, String userId) {
        CardConfirmation row = require(transactionId);
        if (userId != null && !userId.isBlank() && row.getUserId() != null && !row.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This payment belongs to another customer");
        }
        row.setSeen(true);
        confirmations.save(row);
        return view(row);
    }

    @Transactional
    public void expireDue() {
        for (CardConfirmation row : confirmations.findByStatusIn(List.of("PENDING", "CONFIRMATION_REQUIRED"))) {
            if (!expired(row) || !openForDecision(row.getStatus())) {
                continue;
            }
            row.setStatus("EXPIRED");
            row.setOperationState("CANCELLED");
            confirmations.save(row);
            if (row.getClientReference() == null) {
                continue;
            }
            try {
                payments.resumeAuthorization(row.getClientReference(), false);
            } catch (RuntimeException ignored) {
                // The screen stays closed. A later decision cannot reopen an expired payment.
            }
        }
    }

    public Map<String, Object> expire(String transactionId) {
        CardConfirmation row = require(transactionId);
        if (openForDecision(row.getStatus())) {
            row.setStatus("EXPIRED");
            row.setOperationState("CANCELLED");
            confirmations.save(row);
        }
        return view(row);
    }

    private CardConfirmation require(String transactionId) {
        return confirmations.findByTransactionId(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Confirmation not found"));
    }

    private CardConfirmation findExisting(String transactionId, String clientReference) {
        if (clientReference != null) {
            CardConfirmation byReference = confirmations.findFirstByClientReferenceOrderByCreatedAtDesc(clientReference).orElse(null);
            if (byReference != null) {
                return byReference;
            }
        }
        if (transactionId == null) {
            return null;
        }
        return confirmations.findByTransactionId(transactionId).orElse(null);
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
        body.put("availableBalance", row.getAvailableBalance());
        body.put("status", row.getStatus());
        body.put("operationState", row.getOperationState() == null ? row.getStatus() : row.getOperationState());
        body.put("createdAt", row.getCreatedAt());
        body.put("expiresAt", row.getExpiresAt());
        return body;
    }

    private static boolean expired(CardConfirmation row) {
        Instant deadline = row.getExpiresAt();
        if (deadline == null && row.getCreatedAt() != null) {
            deadline = row.getCreatedAt().plus(CONFIRMATION_WINDOW);
        }
        return deadline != null && !deadline.isAfter(Instant.now());
    }

    private static boolean openForDecision(String status) {
        return "PENDING".equals(status) || "CONFIRMATION_REQUIRED".equals(status);
    }

    private static boolean terminal(String status) {
        return "ACCEPTED".equals(status) || "CLAIMED".equals(status) || "BLOCKED".equals(status)
                || "CANCELLED".equals(status) || "REJECTED".equals(status) || "EXPIRED".equals(status)
                || "INSUFFICIENT_BALANCE".equals(status);
    }

    private static String stateOf(Map<String, Object> body) {
        String state = text(body.get("operationState"));
        return state == null ? "CONFIRMATION_REQUIRED" : state;
    }

    private static String statusFor(String state) {
        return switch (state) {
            case "FUNDS_BLOCKED" -> "BLOCKED";
            case "CONFIRMED" -> "ACCEPTED";
            case "CANCELLED" -> "CANCELLED";
            case "INSUFFICIENT_BALANCE" -> "INSUFFICIENT_BALANCE";
            default -> "CONFIRMATION_REQUIRED";
        };
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
