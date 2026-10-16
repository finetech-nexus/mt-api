package fr.nbank.mtapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import fr.nbank.mtapi.domain.MtTransaction;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChannelClientTest {

    @Test
    void partialCaptureIsSentWithTheCaptureAmount() {
        MtTransaction row = new MtTransaction();
        row.setOperation("CAPTURE");
        row.setExternalId("MT-E479D75A-1EF");
        row.setAmount(new BigDecimal("30.000"));
        row.setCurrency("TND");
        Map<String, Object> body = new HashMap<>();
        body.put("partialCapture", true);
        body.put("holdTransferCode", "65e228cd-4a0f-45bc-9aaa-387447a17605");
        Map<String, Object> payload = ChannelClient.inboundPayload(row, body);
        assertEquals(Boolean.TRUE, payload.get("partialCapture"));
        assertEquals("30.000", payload.get("amount"));
        assertEquals("65e228cd-4a0f-45bc-9aaa-387447a17605", payload.get("holdTransferCode"));
    }
}
