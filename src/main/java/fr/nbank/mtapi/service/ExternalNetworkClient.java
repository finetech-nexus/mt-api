package fr.nbank.mtapi.service;

import fr.nbank.mtapi.config.MtProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/** Sends the card message to the external Monétique Tunisie host. Sandbox host is the simulator. */
@Service
public class ExternalNetworkClient {
    private static final Logger log = LoggerFactory.getLogger(ExternalNetworkClient.class);
    private final RestClient http;
    private final MtProperties properties;

    public ExternalNetworkClient(RestClient.Builder builder, MtProperties properties) {
        this.http = builder.build();
        this.properties = properties;
    }

    @Async("mtExecutor")
    public void dispatch(String operation, String clientReference, Map<String, Object> payload) {
        String base = properties.getExternal().getBaseUrl().replaceAll("/+$", "");
        String callback = properties.getCallbackBaseUrl().replaceAll("/+$", "") + "/mt/v1/callbacks";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rail", "MT");
        body.put("operation", operation);
        body.put("clientReference", clientReference);
        body.put("callbackUrl", callback);
        body.put("payload", payload);
        try {
            http.post().uri(base + "/simulate/mt/call").body(body).retrieve().toBodilessEntity();
            log.info("MT network call accepted operation={} ref={}", operation, clientReference);
        } catch (Exception ex) {
            log.warn("MT network call failed ref={}: {}", clientReference, ex.getMessage());
        }
    }

    public Map<String, Object> issue(Map<String, Object> body) {
        if (!properties.getExternal().isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "MT external network is disabled");
        }
        String base = properties.getExternal().getBaseUrl().replaceAll("/+$", "");
        try {
            Map<String, Object> issued = http.post().uri(base + "/simulate/mt/cards").body(body).retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (issued == null || issued.get("pan") == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Simulator did not issue the card");
            }
            return issued;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("MT card issue failed: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Simulator did not issue the card");
        }
    }
}
