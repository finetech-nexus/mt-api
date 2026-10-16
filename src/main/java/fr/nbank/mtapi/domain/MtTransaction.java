package fr.nbank.mtapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "mt_transaction", indexes = {
        @Index(name = "idx_mt_tx_followup", columnList = "original_external_id, operation")
})
public class MtTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Direction direction;

    @Column(nullable = false)
    private String operation;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private String reference;

    @Column(nullable = false, unique = true)
    private String externalId;

    private String authorizationId;
    private String originalExternalId;
    private String panToken;
    private String merchantId;
    private String terminalId;
    private String cardholderName;
    private String responseCode;
    private String authCode;
    private String stan;
    private String rrn;
    private String mti;
    private String reasonMessage;
    private String callbackUrl;
    private String networkReference;
    private String holdTransferCode;

    @Column(columnDefinition = "text")
    private String requestJson;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    /** True when no capture, refund, or void points at this transaction code. Not stored. */
    @Transient
    private boolean voidable;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Direction getDirection() { return direction; }
    public void setDirection(Direction direction) { this.direction = direction; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getAuthorizationId() { return authorizationId; }
    public void setAuthorizationId(String authorizationId) { this.authorizationId = authorizationId; }
    public String getOriginalExternalId() { return originalExternalId; }
    public void setOriginalExternalId(String originalExternalId) { this.originalExternalId = originalExternalId; }
    public String getPanToken() { return panToken; }
    public void setPanToken(String panToken) { this.panToken = panToken; }
    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
    public String getTerminalId() { return terminalId; }
    public void setTerminalId(String terminalId) { this.terminalId = terminalId; }
    public String getCardholderName() { return cardholderName; }
    public void setCardholderName(String cardholderName) { this.cardholderName = cardholderName; }
    public String getResponseCode() { return responseCode; }
    public void setResponseCode(String responseCode) { this.responseCode = responseCode; }
    public String getAuthCode() { return authCode; }
    public void setAuthCode(String authCode) { this.authCode = authCode; }
    public String getStan() { return stan; }
    public void setStan(String stan) { this.stan = stan; }
    public String getRrn() { return rrn; }
    public void setRrn(String rrn) { this.rrn = rrn; }
    public String getMti() { return mti; }
    public void setMti(String mti) { this.mti = mti; }
    public String getReasonMessage() { return reasonMessage; }
    public void setReasonMessage(String reasonMessage) { this.reasonMessage = reasonMessage; }
    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }
    public String getNetworkReference() { return networkReference; }
    public void setNetworkReference(String networkReference) { this.networkReference = networkReference; }
    public String getHoldTransferCode() { return holdTransferCode; }
    public void setHoldTransferCode(String holdTransferCode) { this.holdTransferCode = holdTransferCode; }
    public String getRequestJson() { return requestJson; }
    public void setRequestJson(String requestJson) { this.requestJson = requestJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public boolean isVoidable() { return voidable; }
    public void setVoidable(boolean voidable) { this.voidable = voidable; }
}
