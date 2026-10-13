package fr.nbank.mtapi.api;

import fr.nbank.mtapi.service.MtPaymentService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Connector-facing Monétique Tunisie gateway. */
@RestController
@RequestMapping("/mt/v1")
public class GatewayController {
    private final MtPaymentService payments;

    public GatewayController(MtPaymentService payments) {
        this.payments = payments;
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
}
