package fr.nbank.mtapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mt")
public class MtProperties {
    private String merchantId = "NEXUS-MERCHANT";
    private String callbackBaseUrl = "http://localhost:8097";
    private External external = new External();
    private Channel channel = new Channel();

    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
    public String getCallbackBaseUrl() { return callbackBaseUrl; }
    public void setCallbackBaseUrl(String callbackBaseUrl) { this.callbackBaseUrl = callbackBaseUrl; }
    public External getExternal() { return external; }
    public void setExternal(External external) { this.external = external; }
    public Channel getChannel() { return channel; }
    public void setChannel(Channel channel) { this.channel = channel; }

    public static class External {
        private boolean enabled = true;
        private String baseUrl = "http://localhost:8090";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    public static class Channel {
        private boolean enabled = true;
        private String baseUrl = "http://localhost:8080";
        private String tenant = "nexusbank";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getTenant() { return tenant; }
        public void setTenant(String tenant) { this.tenant = tenant; }
    }
}
