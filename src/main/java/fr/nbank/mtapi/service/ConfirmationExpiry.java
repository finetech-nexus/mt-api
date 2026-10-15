package fr.nbank.mtapi.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Closes a confirmation once its window has passed, so the phone stops showing it. */
@Component
public class ConfirmationExpiry {
    private final CardConfirmationService confirmations;

    public ConfirmationExpiry(CardConfirmationService confirmations) {
        this.confirmations = confirmations;
    }

    @Scheduled(fixedDelay = 15000)
    public void expire() {
        confirmations.expireDue();
    }
}
