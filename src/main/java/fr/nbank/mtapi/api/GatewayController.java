package fr.nbank.mtapi.api;

import fr.nbank.mtapi.service.CardConfirmationService;
import fr.nbank.mtapi.service.MtPaymentService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Connector-facing Monétique Tunisie gateway. */
@RestController
@RequestMapping("/mt/v1")
public class GatewayController {
    private final MtPaymentService payments;
    private final CardConfirmationService confirmations;

    public GatewayController(MtPaymentService payments, CardConfirmationService confirmations) {
        this.payments = payments;
        this.confirmations = confirmations;
    }

    @PostMapping("/authorizations")
    public ResponseEntity<Map<String, Object>> authorize(@RequestBody Map<String, Object> body) {
        return ResponseEntity.accepted().body(payments.authorize(body));
    }

    @PostMapping("/captures")
    public ResponseEntity<Map<String, Object>> capture(@RequestBody Map<String, Object> body) {
        return ResponseEntity.accepted().body(payments.capture(body));
    }

    @PostMapping("/refunds")
    public ResponseEntity<Map<String, Object>> refund(@RequestBody Map<String, Object> body) {
        return ResponseEntity.accepted().body(payments.refund(body));
    }

    @PostMapping("/voids")
    public ResponseEntity<Map<String, Object>> voidTransaction(@RequestBody Map<String, Object> body) {
        return ResponseEntity.accepted().body(payments.voidTransaction(body));
    }

    @GetMapping("/transactions/{id}")
    public Map<String, Object> status(@PathVariable String id) {
        return payments.status(id);
    }

    @PostMapping("/callbacks")
    public Map<String, Object> callback(@RequestBody Map<String, Object> body) {
        return payments.onNetworkCallback(body);
    }

    @PostMapping("/cards")
    public Map<String, Object> issue(@RequestBody Map<String, Object> body) {
        return payments.issue(body);
    }

    @PostMapping("/confirmations")
    public Map<String, Object> openConfirmation(@RequestBody Map<String, Object> body) {
        return confirmations.open(body);
    }

    @GetMapping("/confirmations")
    public Map<String, Object> pendingConfirmations(@RequestParam String userId) {
        return confirmations.list(userId);
    }

    @GetMapping("/confirmations/{transactionId}")
    public Map<String, Object> confirmation(@PathVariable String transactionId) {
        return confirmations.one(transactionId);
    }

    @PostMapping("/confirmations/claim")
    public Map<String, Object> claim(@RequestBody Map<String, Object> body) {
        return confirmations.claim(body);
    }

    @PostMapping("/confirmations/blocked")
    public Map<String, Object> blocked(@RequestBody Map<String, Object> body) {
        return confirmations.markBlocked(body);
    }

    @PostMapping("/confirmations/{transactionId}/seen")
    public Map<String, Object> seen(@PathVariable String transactionId, @RequestBody(required = false) Map<String, Object> body) {
        String userId = body == null || body.get("userId") == null ? null : String.valueOf(body.get("userId"));
        return confirmations.seen(transactionId, userId);
    }

    @PostMapping("/confirmations/{transactionId}/decision")
    public Map<String, Object> decide(@PathVariable String transactionId, @RequestBody Map<String, Object> body) {
        boolean accepted = Boolean.TRUE.equals(body.get("accepted")) || "true".equalsIgnoreCase(String.valueOf(body.get("accepted")));
        String userId = body.get("userId") == null ? null : String.valueOf(body.get("userId"));
        return confirmations.decide(transactionId, accepted, userId);
    }

    @PostMapping("/confirmations/{transactionId}/expire")
    public Map<String, Object> expire(@PathVariable String transactionId) {
        return confirmations.expire(transactionId);
    }
}
