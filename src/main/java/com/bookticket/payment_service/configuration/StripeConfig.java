package com.bookticket.payment_service.configuration;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "stripe.api")
@Getter
@Setter
public class StripeConfig {
    private String secretKey;
    private String publicKey;
//    private String returnUrl; // TODO Swagger UI
    private String successUrl;  // For Checkout Session
    private String cancelUrl;   // For Checkout Session
    private Integer checkoutSessionExpiryMinutes;  // Checkout session expiry time in minutes

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
    }
}
