package com.bookticket.payment_service.repository;

import com.bookticket.payment_service.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bookticket.payment_service.enums.PaymentStatus;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByTransactionId(String transactionId);
    Optional<Payment> findByBookingId(Long bookingId);
    List<Payment> findByPaymentStatus(PaymentStatus status);
}
