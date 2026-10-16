package fr.nbank.mtapi.service;

import fr.nbank.mtapi.config.MtProperties;
import fr.nbank.mtapi.domain.MtTransaction;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Asks the card service whether this inbound operation is allowed before the workflow starts.
 * A decline is returned to the switch. The PIN is sent only on this call and is not stored.
 */
@Component
public class CardControlClient {
    private final RestClient http;
    private final boolean enabled;

    public CardControlClient(RestClient.Builder builder, MtProperties properties) {
        String baseUrl = properties.getCards().getBaseUrl() == null ? "" : properties.getCards().getBaseUrl().trim();
        this.enabled = properties.getCards().isEnabled() && !baseUrl.isBlank();
        this.http = builder.baseUrl(trim(baseUrl)).build();
    }

    public Decline check(MtTransaction row, Map<String, Object> body) {
        if (!enabled || row.getDirection() == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pan", first(body.get("pan"), body.get("panToken"), row.getPanToken()));
        payload.put("amount", row.getAmount() == null ? "0" : row.getAmount().toPlainString());
        payload.put("currency", row.getCurrency());
        payload.put("operation", row.getOperation());
        payload.put("entryMode", first(body.get("entryMode"), body.get("channel"), body.get("posEntryMode")));
        payload.put("operationType", first(body.get("operationType"), body.get("channel")));
        payload.put("international", truth(body.get("international")));
        payload.put("clientReference", row.getExternalId());
        String pin = text(body.get("pin"));
        if (!pin.isBlank()) {
            payload.put("pin", pin);
        }
        try {
            http.post().uri("/internal/cards/check").contentType(MediaType.APPLICATION_JSON).body(payload).retrieve()
                    .toBodilessEntity();
            return null;
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 409 || status == 404) {
                String code = code(ex.getResponseBodyAsString());
                if (status == 404) {
                    code = "CARD_NOT_FOUND";
                }
                return new Decline(responseCode(code), message(ex.getResponseBodyAsString(), code));
            }
            return unavailable(row);
        } catch (RuntimeException ex) {
            return unavailable(row);
        }
    }

    private static Decline unavailable(MtTransaction row) {
        String operation = row.getOperation() == null ? "" : row.getOperation();
        if ("REFUND".equals(operation) || "VOID".equals(operation)) {
            return null;
        }
        return new Decline("05", "Card control unavailable");
    }

    public static String responseCode(String code) {
        if (code == null) {
            return "05";
        }
        if (code.startsWith("LIMIT_")) {
            return "61";
        }
        if (code.startsWith("CHANNEL_")) {
            return "57";
        }
        if ("INVALID_AMOUNT".equals(code)) {
            return "13";
        }
        if ("INVALID_PIN".equals(code)) {
            return "55";
        }
        if ("CARD_NOT_FOUND".equals(code)) {
            return "14";
        }
        return "05";
    }

    private static String message(String body, String code) {
        String error = field(body, "error");
        if (!error.isBlank()) {
            return error;
        }
        return switch (code) {
            case "CARD_FROZEN" -> "Card is frozen";
            case "CARD_INACTIVE" -> "Card is not active";
            case "CARD_NOT_FOUND" -> "Card not found";
            case "INVALID_PIN" -> "ATM PIN is incorrect";
            case "LIMIT_DAILY_AMOUNT" -> "Daily amount limit exceeded";
            case "LIMIT_MONTHLY_AMOUNT" -> "Monthly amount limit exceeded";
            case "LIMIT_DAILY_COUNT" -> "Daily number of operations exceeded";
            case "CHANNEL_CONTACTLESS" -> "Contactless is not allowed on this card";
            case "CHANNEL_ONLINE" -> "Online payments are not allowed on this card";
            case "CHANNEL_INTERNATIONAL" -> "International payments are not allowed on this card";
            case "CHANNEL_ATM" -> "ATM withdrawals are not allowed on this card";
            case "CURRENCY_MISMATCH" -> "The amount currency does not match the card";
            case "INVALID_AMOUNT" -> "A positive amount is required";
            default -> "Card declined";
        };
    }

    private static String code(String body) {
        String value = field(body, "code");
        return value.isBlank() ? "CARD_DECLINED" : value;
    }

    private static String field(String body, String name) {
        if (body == null) {
            return "";
        }
        String mark = "\"" + name + "\":\"";
        int start = body.indexOf(mark);
        if (start < 0) {
            return "";
        }
        start += mark.length();
        int end = body.indexOf('"', start);
        return end > start ? body.substring(start, end) : "";
    }

    private static boolean truth(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(text(value));
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
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
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    public record Decline(String responseCode, String message) {
    }
}
