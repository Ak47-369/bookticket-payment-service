package com.bookticket.payment_service.service;

import com.bookticket.payment_service.dto.*;

public interface PaymentService {
    // SECURE APPROACH: Checkout Session (Recommended for backend-focused)
    CheckoutSessionResponse createCheckoutSession(CheckoutSessionRequest request);
    com.bookticket.booking_service.dto.PaymentResponse verifyCheckoutSession(String sessionId);
//    PaymentResponse processPayment(PaymentRequest paymentRequest);

//    // Common methods
//    PaymentResponse verifyPayment(String paymentId);
    default com.bookticket.booking_service.dto.PaymentResponse getPaymentStatus(String transactionId){
        return null;
    }
}
