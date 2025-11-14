package com.bookticket.payment_service.entity;

import com.bookticket.payment_service.enums.PaymentStatus;
import jakarta.persistence.*;

@Entity
@Table(name = "payments")
public class Payment {
    @Column(nullable = false)
    private Long bookingId;
    @Column(nullable = false)
    private Double amount;
    @Column(nullable = false)
    private String paymentMethod; // STRIPE, RAZORPAY, PAYPAL, CC, etc.
    private String transactionId; // From Stripe/Razorpay
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;
}
