package fr.nbank.mtapi.service;

import fr.nbank.mtapi.config.MtProperties;
import fr.nbank.mtapi.domain.MtTransaction;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/** Hands an inbound Monétique Tunisie message to the channel, which starts the BPMN. */
@Component
public class ChannelClient {
    private final RestClient http;
    private final MtProperties properties;

    public ChannelClient(RestClient.Builder builder, MtProperties properties) {
        this.properties = properties;
        this.http = builder.baseUrl(trim(properties.getChannel().getBaseUrl())).build();
    }

    public String start(MtTransaction row, Map<String, Object> body) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operation", row.getOperation());
        payload.put("clientReference", row.getExternalId());
        payload.put("amount", row.getAmount() == null ? "0" : row.getAmount().toPlainString());
        payload.put("currency", row.getCurrency());
        payload.put("pan", first(body.get("pan"), body.get("panToken"), row.getPanToken()));
        payload.put("panToken", first(body.get("panToken"), body.get("pan"), row.getPanToken()));
        payload.put("merchantId", row.getMerchantId());
        payload.put("cardholderName", row.getCardholderName());
        payload.put("authorizationId", row.getAuthorizationId());
        payload.put("originalTransactionId", row.getOriginalExternalId());
        payload.put("entryMode", first(body.get("entryMode"), body.get("channel")));
        payload.put("ecommerce", ecommerce(body));
        payload.put("terminalId", first(body.get("terminalId")));
        boolean decided = body.containsKey("userConfirmed") && body.get("userConfirmed") != null
                && !String.valueOf(body.get("userConfirmed")).isBlank();
        payload.put("awaitingDecision", !decided);
        if (decided) {
            payload.put("userConfirmed", truth(body.get("userConfirmed")));
        }
        if (body.containsKey("expired")) {
            payload.put("expired", truth(body.get("expired")));
        }
        if (body.get("holdTransferCode") != null && !String.valueOf(body.get("holdTransferCode")).isBlank()) {
            payload.put("holdTransferCode", body.get("holdTransferCode"));
        }
        if (body.containsKey("confirmationRequired")) {
            payload.put("confirmationRequired", truth(body.get("confirmationRequired")));
        } else {
            payload.put("confirmationRequired", ecommerce(body));
        }
        try {
            Map<?, ?> response = http.post()
                    .uri("/channel/mt/inbound")
                    .header("Platform-TenantId", properties.getChannel().getTenant())
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
            if (response == null || response.get("transactionId") == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Channel did not start a workflow");
            }
            return String.valueOf(response.get("transactionId"));
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Channel is unavailable");
        }
    }

    public void publishDecision(String transactionId, boolean accepted) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transactionId", transactionId);
        payload.put("accepted", accepted);
        try {
            http.post().uri("/channel/mt/decision").body(payload).retrieve().toBodilessEntity();
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Channel did not accept the cardholder decision");
        }
    }

    private static boolean truth(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static boolean ecommerce(Map<String, Object> body) {
        if (Boolean.TRUE.equals(body.get("ecommerce"))) {
            return true;
        }
        String mode = String.valueOf(first(body.get("entryMode"), body.get("channel"), "")).toUpperCase(Locale.ROOT);
        return mode.equals("ECOM") || mode.equals("ECOMMERCE") || mode.equals("ONLINE") || mode.equals("CNP");
    }

    private static Object first(Object... values) {
        for (Object value : values) {
            if (value != null && !String.valueOf(value).isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String trim(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:8080";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
