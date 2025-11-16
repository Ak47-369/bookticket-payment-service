package com.bookticket.payment_service.controller;

import com.bookticket.booking_service.dto.PaymentResponse;
import com.bookticket.payment_service.dto.CheckoutSessionRequest;
import com.bookticket.payment_service.dto.CheckoutSessionResponse;
import com.bookticket.payment_service.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // ============================================================================
    // SECURE APPROACH: Checkout Session (RECOMMENDED for Production)
    // ============================================================================

    /**
     * Create Checkout Session - SECURE & BACKEND-FOCUSED
     *
     * This is the RECOMMENDED approach for production:
     * 1. Backend creates a Checkout Session
     * 2. Returns a payment URL
     * 3. Client redirects user to the URL
     * 4. User enters card details on Stripe's secure page
     * 5. Stripe redirects back to success/cancel URL
     * 6. Backend verifies payment status
     *
     * Advantages:
     * - 100% PCI Compliant (card details never touch your server)
     * - No frontend code needed
     * - Stripe handles all payment UI, validation, 3D Secure
     * - Production-ready
     */
    @PostMapping("/checkout/create")
    public ResponseEntity<CheckoutSessionResponse> createCheckoutSession(
            @Valid @RequestBody CheckoutSessionRequest request) {
        CheckoutSessionResponse response = paymentService.createCheckoutSession(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Verify Checkout Session after user completes payment
     * Call this endpoint after user is redirected back from Stripe's payment page
     */
    @GetMapping("/checkout/verify/{sessionId}")
    public ResponseEntity<PaymentResponse> verifyCheckoutSession(@PathVariable String sessionId) {
        PaymentResponse response = paymentService.verifyCheckoutSession(sessionId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get payment status from database
     */
    @GetMapping("/status/{transactionId}")
    public ResponseEntity<PaymentResponse> getPaymentStatus(@PathVariable String transactionId) {
        PaymentResponse response = paymentService.getPaymentStatus(transactionId);
        return ResponseEntity.ok(response);
    }
}
