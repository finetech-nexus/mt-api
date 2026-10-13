package fr.nbank.mtapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import fr.nbank.mtapi.domain.Direction;
import fr.nbank.mtapi.service.MtPaymentService;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mt;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "mt.external.enabled=false"
})
class MtPaymentServiceTest {
    @Autowired
    private MtPaymentService payments;

    @Test
    void authorizeCaptureVoidAndFilter() {
        Map<String, Object> body = new HashMap<>();
        body.put("orderId", UUID.randomUUID().toString());
        body.put("amount", new BigDecimal("12.500"));
        body.put("currency", "TND");
        body.put("panToken", "tok_pan_4242");
        body.put("merchantId", "M-1");
        body.put("cardholderName", "Amina");
        Map<String, Object> authorized = payments.authorize(body);
        assertEquals("AUTHORIZED", authorized.get("status"));
        assertEquals("00", authorized.get("responseCode"));

        Map<String, Object> captureBody = new HashMap<>();
        captureBody.put("authorizationId", authorized.get("transactionId"));
        captureBody.put("amount", new BigDecimal("12.500"));
        Map<String, Object> captured = payments.capture(captureBody);
        assertEquals("CAPTURED", captured.get("status"));

        Map<String, Object> voidBody = new HashMap<>();
        voidBody.put("originalTransactionId", authorized.get("transactionId"));
        Map<String, Object> voided = payments.voidTransaction(voidBody);
        assertEquals("PENDING", voided.get("status"));

        Map<String, Object> callback = new HashMap<>();
        callback.put("clientReference", voided.get("transactionId"));
        callback.put("outcome", "SETTLED");
        assertEquals("VOIDED", payments.onNetworkCallback(callback).get("status"));
        assertEquals("VOIDED", payments.status(String.valueOf(authorized.get("transactionId"))).get("status"));

        var page = payments.search(Direction.OUTBOUND, null, "AUTHORIZE", "Amina", null, null, PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
    }

    @Test
    void duplicateOrderRejected() {
        String orderId = UUID.randomUUID().toString();
        Map<String, Object> body = card(orderId);
        payments.authorize(body);
        assertThrows(ResponseStatusException.class, () -> payments.authorize(card(orderId)));
    }

    @Test
    void missingPanRejected() {
        Map<String, Object> body = new HashMap<>();
        body.put("amount", "10");
        body.put("currency", "TND");
        body.put("merchantId", "M-1");
        assertThrows(ResponseStatusException.class, () -> payments.authorize(body));
    }

    private static Map<String, Object> card(String orderId) {
        Map<String, Object> body = new HashMap<>();
        body.put("orderId", orderId);
        body.put("amount", "5.000");
        body.put("currency", "TND");
        body.put("panToken", "tok");
        body.put("merchantId", "M-1");
        return body;
    }
}
