package com.bookticket.payment_service.service;

import com.bookticket.payment_service.dto.CheckoutSessionRequest;
import com.bookticket.payment_service.dto.CheckoutSessionResponse;
import com.bookticket.payment_service.dto.PaymentResponse;

public interface PaymentService {
    // SECURE APPROACH: Checkout Session (Recommended for backend-focused)
    CheckoutSessionResponse createCheckoutSession(CheckoutSessionRequest request);
    PaymentResponse verifyCheckoutSession(String sessionId);
//    PaymentResponse processPayment(PaymentRequest paymentRequest);

//    // Common methods
//    PaymentResponse verifyPayment(String paymentId);
    default PaymentResponse getPaymentStatus(String transactionId){
        return null;
    }
}
