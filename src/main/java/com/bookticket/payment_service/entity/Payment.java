package com.bookticket.payment_service.entity;

import com.bookticket.payment_service.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment extends Auditable{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long id;
    @Column(nullable = false)
    private Long bookingId;
    @Column(nullable = false)
    private Double amount;
    @Column(nullable = false)
    private String paymentMethod; // STRIPE, RAZORPAY, PAYPAL, CC, etc.
    private String transactionId; // From Stripe/Razorpay
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;
    @Column(columnDefinition = "TEXT")
    private String paymentGatewayResponse; // Raw response from Stripe/Razorpay
    @Column(nullable = false)
    private Long userId;
}
