package fr.nbank.mtapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.nbank.mtapi.config.MtProperties;
import fr.nbank.mtapi.domain.Direction;
import fr.nbank.mtapi.domain.MtTransaction;
import fr.nbank.mtapi.repo.MtTransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Monétique Tunisie authorization lifecycle. The connector contract is the REST façade
 * over ISO 8583: 0100 authorization, 0220 capture, 0200 refund, 0400 void, and host inquiry.
 */
@Service
public class MtPaymentService {
    private final MtTransactionRepository transactions;
    private final ExternalNetworkClient network;
    private final CallbackForwarder callbacks;
    private final OperationLogService operationLog;
    private final MtProperties properties;
    private final ObjectMapper json;

    public MtPaymentService(MtTransactionRepository transactions, ExternalNetworkClient network,
            CallbackForwarder callbacks, OperationLogService operationLog, MtProperties properties, ObjectMapper json) {
        this.transactions = transactions;
        this.network = network;
        this.callbacks = callbacks;
        this.operationLog = operationLog;
        this.properties = properties;
        this.json = json;
    }

    public Map<String, Object> authorize(Map<String, Object> body) {
        return createCard(body, "AUTHORIZE", Direction.OUTBOUND, true);
    }

    public Map<String, Object> inbound(Map<String, Object> body) {
        String operation = text(body.get("operation"));
        String normalized = operation == null ? "AUTHORIZE" : operation.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "REFUND" -> refund(withDirection(body, Direction.INBOUND), false);
            case "VOID" -> voidTransaction(withDirection(body, Direction.INBOUND), false);
            case "CAPTURE" -> capture(withDirection(body, Direction.INBOUND), false);
            default -> createCard(body, "AUTHORIZE", Direction.INBOUND, false);
        };
    }

    public Map<String, Object> capture(Map<String, Object> body) {
        return capture(body, true);
    }

    public Map<String, Object> refund(Map<String, Object> body) {
        return refund(body, true);
    }

    public Map<String, Object> voidTransaction(Map<String, Object> body) {
        return voidTransaction(body, true);
    }

    public Map<String, Object> status(String id) {
        return toStatus(findBusiness(id));
    }

    public MtTransaction get(Long id) {
        return transactions.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
    }

    public Map<String, Object> onNetworkCallback(Map<String, Object> body) {
        String ref = text(first(body.get("clientReference"), body.get("transactionId"), body.get("orderId")));
        if (ref == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Callback requires clientReference");
        }
        MtTransaction row = findBusiness(ref);
        String outcome = text(body.getOrDefault("outcome", "SETTLED"));
        if ("REJECTED".equalsIgnoreCase(outcome) || "FAILED".equalsIgnoreCase(outcome)) {
            row.setStatus("FAILED");
            row.setResponseCode(text(body.getOrDefault("reasonCode", "05")));
            row.setReasonMessage("Do not honor");
        } else {
            applyFinal(row);
        }
        if (body.get("networkReference") != null) {
            row.setNetworkReference(text(body.get("networkReference")));
        }
        row.setUpdatedAt(Instant.now());
        transactions.save(row);
        Map<String, Object> status = toStatus(row);
        callbacks.send(row.getCallbackUrl(), status);
        operationLog.record("network", "CALLBACK", row.getExternalId() + " " + row.getStatus());
        return status;
    }

    public Page<MtTransaction> search(Direction direction, String status, String operation, String q,
            LocalDate from, LocalDate to, Pageable pageable) {
        Instant fromTs = from == null ? Instant.EPOCH : from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toTs = to == null ? Instant.parse("9999-12-31T00:00:00Z") : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Specification<MtTransaction> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (direction != null) {
                predicates.add(cb.equal(root.get("direction"), direction));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("status")), status.toLowerCase(Locale.ROOT)));
            }
            if (operation != null && !operation.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("operation")), operation.toLowerCase(Locale.ROOT)));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("reference")), like),
                        cb.like(cb.lower(root.get("externalId")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("panToken"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("merchantId"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("cardholderName"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("authCode"), "")), like)));
            }
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromTs));
            predicates.add(cb.lessThan(root.get("createdAt"), toTs));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return transactions.findAll(spec, pageable);
    }

    private Map<String, Object> capture(Map<String, Object> body, boolean callNetwork) {
        String authId = required(body.get("authorizationId"), "authorizationId");
        MtTransaction original = findBusiness(authId);
        if (!"AUTHORIZE".equals(original.getOperation()) || !"AUTHORIZED".equals(original.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Capture requires an authorized transaction");
        }
        BigDecimal amount = body.get("amount") == null ? original.getAmount() : money(body.get("amount"));
        if (amount.compareTo(original.getAmount()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Capture amount exceeds the authorization");
        }
        MtTransaction row = base(original.getReference() + "-CAP", amount, original.getCurrency(), directionOf(body, Direction.OUTBOUND));
        row.setOperation("CAPTURE");
        row.setStatus("CAPTURED");
        row.setMti("0220");
        row.setResponseCode("00");
        row.setReasonMessage("Capture accepted");
        row.setAuthorizationId(original.getExternalId());
        row.setOriginalExternalId(original.getExternalId());
        row.setPanToken(original.getPanToken());
        row.setMerchantId(original.getMerchantId());
        row.setTerminalId(original.getTerminalId());
        row.setCardholderName(original.getCardholderName());
        row.setCallbackUrl(text(first(body.get("callbackUrl"), original.getCallbackUrl())));
        return finish(row, body, callNetwork);
    }

    private Map<String, Object> refund(Map<String, Object> body, boolean callNetwork) {
        BigDecimal amount = money(body.get("amount"));
        String currency = required(body.getOrDefault("currency", "TND"), "currency");
        String originalId = text(body.get("originalTransactionId"));
        MtTransaction row = base(text(body.getOrDefault("orderId", "refund-" + UUID.randomUUID())), amount, currency,
                directionOf(body, Direction.OUTBOUND));
        row.setOperation("REFUND");
        row.setStatus("PENDING");
        row.setMti("0200");
        row.setResponseCode("00");
        row.setReasonMessage("Refund accepted for processing");
        row.setPanToken("refund");
        row.setMerchantId(text(body.getOrDefault("merchantId", "refund")));
        row.setOriginalExternalId(originalId);
        row.setCallbackUrl(text(body.get("callbackUrl")));
        if (originalId != null) {
            transactions.findByExternalId(originalId).ifPresent(original -> {
                row.setPanToken(original.getPanToken());
                row.setMerchantId(original.getMerchantId());
                row.setCardholderName(original.getCardholderName());
            });
        }
        return finish(row, body, callNetwork);
    }

    private Map<String, Object> voidTransaction(Map<String, Object> body, boolean callNetwork) {
        String originalId = required(body.get("originalTransactionId"), "originalTransactionId");
        MtTransaction original = findBusiness(originalId);
        if ("VOIDED".equals(original.getStatus()) || "FAILED".equals(original.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transaction can no longer be voided");
        }
        MtTransaction row = base(original.getReference() + "-REV", original.getAmount(), original.getCurrency(),
                directionOf(body, Direction.OUTBOUND));
        row.setOperation("VOID");
        row.setStatus("PENDING");
        row.setMti("0400");
        row.setResponseCode("00");
        row.setReasonMessage("Void accepted for processing");
        row.setOriginalExternalId(original.getExternalId());
        row.setAuthorizationId(text(body.getOrDefault("authorizationId", original.getAuthorizationId())));
        row.setPanToken(original.getPanToken());
        row.setMerchantId(original.getMerchantId());
        row.setTerminalId(original.getTerminalId());
        row.setCallbackUrl(text(first(body.get("callbackUrl"), original.getCallbackUrl())));
        return finish(row, body, callNetwork);
    }

    private Map<String, Object> createCard(Map<String, Object> body, String operation, Direction direction, boolean callNetwork) {
        String orderId = text(body.getOrDefault("orderId", UUID.randomUUID().toString()));
        if (orderId == null || orderId.isBlank()) {
            orderId = UUID.randomUUID().toString();
        }
        transactions.findFirstByReferenceAndOperation(orderId, operation).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate orderId " + existing.getReference());
        });
        BigDecimal amount = money(body.get("amount"));
        String currency = required(body.get("currency"), "currency");
        String pan = text(body.get("panToken"));
        String merchant = text(first(body.get("merchantId"), properties.getMerchantId()));
        String terminal = text(body.get("terminalId"));
        if (pan == null || pan.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MT requires panToken");
        }
        if ((merchant == null || merchant.isBlank()) && (terminal == null || terminal.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MT requires merchantId or terminalId");
        }
        MtTransaction row = base(orderId, amount, currency, direction);
        row.setOperation(operation);
        row.setStatus("AUTHORIZED");
        row.setMti("0100");
        row.setResponseCode("00");
        row.setReasonMessage("Authorization approved");
        row.setPanToken(pan);
        row.setMerchantId(merchant);
        row.setTerminalId(terminal);
        row.setCardholderName(text(body.get("cardholderName")));
        row.setCallbackUrl(text(body.get("callbackUrl")));
        row.setAuthorizationId(row.getExternalId());
        return finish(row, body, callNetwork);
    }

    private Map<String, Object> finish(MtTransaction row, Map<String, Object> body, boolean callNetwork) {
        row.setRequestJson(write(body));
        row.setUpdatedAt(Instant.now());
        transactions.save(row);
        if (callNetwork && properties.getExternal().isEnabled() && row.getDirection() == Direction.OUTBOUND) {
            network.dispatch(row.getOperation(), row.getExternalId(), toStatus(row));
        }
        if (row.getDirection() == Direction.INBOUND) {
            callbacks.send(row.getCallbackUrl(), toStatus(row));
        }
        return toStatus(row);
    }

    private void applyFinal(MtTransaction row) {
        switch (row.getOperation()) {
            case "AUTHORIZE" -> {
                row.setStatus("AUTHORIZED");
                row.setResponseCode("00");
                row.setReasonMessage("Authorization held");
            }
            case "REFUND" -> {
                row.setStatus("REFUNDED");
                row.setResponseCode("00");
                row.setReasonMessage("Refund settled");
            }
            case "VOID" -> {
                row.setStatus("VOIDED");
                row.setResponseCode("00");
                row.setReasonMessage("Authorization voided");
                if (row.getOriginalExternalId() != null) {
                    transactions.findByExternalId(row.getOriginalExternalId()).ifPresent(original -> {
                        original.setStatus("VOIDED");
                        original.setReasonMessage("Authorization voided");
                        original.setUpdatedAt(Instant.now());
                        transactions.save(original);
                    });
                }
            }
            default -> {
                row.setStatus("SETTLED");
                row.setResponseCode("00");
                row.setReasonMessage("MT card payment settled");
            }
        }
    }

    private MtTransaction base(String reference, BigDecimal amount, String currency, Direction direction) {
        MtTransaction row = new MtTransaction();
        row.setDirection(direction);
        row.setReference(reference);
        row.setAmount(amount);
        row.setCurrency(currency.toUpperCase(Locale.ROOT));
        row.setExternalId("MT-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT));
        row.setAuthCode(UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT));
        row.setStan(String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000)));
        row.setRrn(String.format("%012d", ThreadLocalRandom.current().nextLong(1_000_000_000_000L)));
        row.setNetworkReference(row.getExternalId());
        return row;
    }

    private MtTransaction findBusiness(String id) {
        return transactions.findByExternalId(id)
                .or(() -> transactions.findFirstByReference(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found: " + id));
    }

    public Map<String, Object> toStatus(MtTransaction row) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("transactionId", row.getExternalId());
        status.put("authorizationId", row.getAuthorizationId() == null ? row.getExternalId() : row.getAuthorizationId());
        status.put("orderId", row.getReference());
        status.put("status", row.getStatus());
        status.put("responseCode", row.getResponseCode());
        status.put("authCode", row.getAuthCode());
        status.put("reasonMessage", row.getReasonMessage());
        status.put("updatedAt", row.getUpdatedAt());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("scheme", "Monétique Tunisie");
        details.put("mti", row.getMti());
        details.put("stan", row.getStan());
        details.put("rrn", row.getRrn());
        details.put("direction", row.getDirection().name());
        details.put("operation", row.getOperation());
        status.put("details", details);
        return status;
    }

    private static Map<String, Object> withDirection(Map<String, Object> body, Direction direction) {
        body.put("direction", direction.name());
        return body;
    }

    private static Direction directionOf(Map<String, Object> body, Direction fallback) {
        String value = text(body.get("direction"));
        if (value == null) {
            return fallback;
        }
        return Direction.valueOf(value.toUpperCase(Locale.ROOT));
    }

    private static BigDecimal money(Object value) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MT amount must be > 0");
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MT amount is not a number");
        }
        if (amount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MT amount must be > 0");
        }
        return amount;
    }

    private static String required(Object value, String name) {
        String text = text(value);
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MT " + name + " required");
        }
        return text;
    }

    private static Object first(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String write(Object body) {
        try {
            return json.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }
}
