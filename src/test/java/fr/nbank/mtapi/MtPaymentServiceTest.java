package fr.nbank.mtapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.nbank.mtapi.domain.Direction;
import fr.nbank.mtapi.repo.MtTransactionRepository;
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
    @Autowired
    private MtTransactionRepository transactions;

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

        Map<String, Object> held = new HashMap<>();
        held.put("clientReference", authorized.get("transactionId"));
        held.put("outcome", "SETTLED");
        held.put("holdTransferCode", "00de21be-41bb-4781-ad47-6fa2636a3b56");
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) payments.onNetworkCallback(held).get("details");
        assertEquals("00de21be-41bb-4781-ad47-6fa2636a3b56", details.get("holdTransferCode"));

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
    void captureBelowTheAuthorizationIsMarkedPartial() {
        Map<String, Object> body = new HashMap<>();
        body.put("orderId", UUID.randomUUID().toString());
        body.put("amount", new BigDecimal("20.000"));
        body.put("currency", "TND");
        body.put("panToken", "tok_pan_4242");
        body.put("merchantId", "M-1");
        Map<String, Object> authorized = payments.authorize(body);
        Map<String, Object> held = new HashMap<>();
        held.put("clientReference", authorized.get("transactionId"));
        held.put("outcome", "SETTLED");
        held.put("holdTransferCode", "54347853-126f-4d37-942d-a391ecf7d3ae");
        payments.onNetworkCallback(held);

        Map<String, Object> captureBody = new HashMap<>();
        captureBody.put("authorizationId", authorized.get("transactionId"));
        captureBody.put("amount", new BigDecimal("15.000"));
        Map<String, Object> captured = payments.capture(captureBody);
        assertEquals("CAPTURED", captured.get("status"));
        String request = transactions.findByExternalId(String.valueOf(captured.get("transactionId"))).orElseThrow().getRequestJson();
        assertTrue(request.contains("\"partialCapture\":true"));
    }

    @Test
    void voidAfterCaptureSkipsTheReleasedHold() {
        Map<String, Object> body = new HashMap<>();
        body.put("orderId", UUID.randomUUID().toString());
        body.put("amount", new BigDecimal("40.000"));
        body.put("currency", "TND");
        body.put("panToken", "tok_pan_4242");
        body.put("merchantId", "M-1");
        Map<String, Object> authorized = payments.authorize(body);
        Map<String, Object> held = new HashMap<>();
        held.put("clientReference", authorized.get("transactionId"));
        held.put("outcome", "SETTLED");
        held.put("holdTransferCode", "e4e4186c-ef56-4ead-9094-4991b1605481");
        payments.onNetworkCallback(held);
        Map<String, Object> captureBody = new HashMap<>();
        captureBody.put("authorizationId", authorized.get("transactionId"));
        captureBody.put("amount", new BigDecimal("38.000"));
        payments.capture(captureBody);

        Map<String, Object> voidBody = new HashMap<>();
        voidBody.put("originalTransactionId", authorized.get("transactionId"));
        Map<String, Object> voided = payments.voidTransaction(voidBody);
        String request = transactions.findByExternalId(String.valueOf(voided.get("transactionId"))).orElseThrow().getRequestJson();
        assertTrue(request.contains("\"holdAlreadyReleased\":true"));
        assertFalse(request.contains("e4e4186c-ef56-4ead-9094-4991b1605481"));
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
