package fr.nbank.mtapi.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class CallbackForwarder {
    private static final Logger log = LoggerFactory.getLogger(CallbackForwarder.class);
    private final RestClient http;

    public CallbackForwarder(RestClient.Builder builder) {
        this.http = builder.build();
    }

    public void send(String callbackUrl, Object body) {
        if (callbackUrl == null || callbackUrl.isBlank()) {
            return;
        }
        try {
            http.post().uri(callbackUrl).body(body).retrieve().toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Callback forward failed url={}: {}", callbackUrl, ex.getMessage());
        }
    }
}
