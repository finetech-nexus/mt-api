package fr.nbank.mtapi.api;

import fr.nbank.mtapi.domain.Direction;
import fr.nbank.mtapi.domain.MtTransaction;
import fr.nbank.mtapi.domain.OperationLog;
import fr.nbank.mtapi.service.MtPaymentService;
import fr.nbank.mtapi.service.OperationLogService;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ConsoleController {
    private final MtPaymentService payments;
    private final OperationLogService operationLog;

    public ConsoleController(MtPaymentService payments, OperationLogService operationLog) {
        this.payments = payments;
        this.operationLog = operationLog;
    }

    @GetMapping("/transactions")
    public Page<MtTransaction> transactions(
            @RequestParam(required = false) Direction direction,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return payments.search(direction, status, operation, q, from, to, page(page, size));
    }

    @DeleteMapping("/transactions")
    public Map<String, Object> purgeTransactions() {
        return Map.of("purged", payments.purge());
    }

    @GetMapping("/transactions/{id}")
    public MtTransaction transaction(@PathVariable Long id) {
        return payments.get(id);
    }

    @PostMapping("/inbound")
    public Map<String, Object> inbound(@RequestHeader(value = "X-User", required = false) String user,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> status = payments.inbound(body);
        operationLog.record(user, "INBOUND", String.valueOf(status.get("transactionId")));
        return status;
    }

    @PostMapping("/transactions/{id}/capture")
    public Map<String, Object> capture(@RequestHeader(value = "X-User", required = false) String user,
            @PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        MtTransaction original = payments.get(id);
        Map<String, Object> payload = body == null ? new HashMap<>() : new HashMap<>(body);
        payload.put("authorizationId", original.getExternalId());
        payload.putIfAbsent("amount", original.getAmount());
        payload.putIfAbsent("callbackUrl", original.getCallbackUrl());
        Map<String, Object> status = payments.capture(payload);
        operationLog.record(user, "CAPTURE", original.getExternalId());
        return status;
    }

    @PostMapping("/transactions/{id}/refund")
    public Map<String, Object> refund(@RequestHeader(value = "X-User", required = false) String user,
            @PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        MtTransaction original = payments.get(id);
        Map<String, Object> payload = body == null ? new HashMap<>() : new HashMap<>(body);
        payload.put("originalTransactionId", original.getExternalId());
        payload.putIfAbsent("amount", original.getAmount());
        payload.putIfAbsent("currency", original.getCurrency());
        payload.putIfAbsent("callbackUrl", original.getCallbackUrl());
        Map<String, Object> status = payments.refund(payload);
        operationLog.record(user, "REFUND", original.getExternalId());
        return status;
    }

    @PostMapping("/transactions/{id}/void")
    public Map<String, Object> voidTransaction(@RequestHeader(value = "X-User", required = false) String user,
            @PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        MtTransaction original = payments.get(id);
        Map<String, Object> payload = body == null ? new HashMap<>() : new HashMap<>(body);
        payload.put("originalTransactionId", original.getExternalId());
        payload.putIfAbsent("callbackUrl", original.getCallbackUrl());
        Map<String, Object> status = payments.voidTransaction(payload);
        operationLog.record(user, "VOID", original.getExternalId());
        return status;
    }

    @GetMapping("/operations")
    public Page<OperationLog> operations(@RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return operationLog.search(q, page(page, size));
    }

    private static PageRequest page(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
