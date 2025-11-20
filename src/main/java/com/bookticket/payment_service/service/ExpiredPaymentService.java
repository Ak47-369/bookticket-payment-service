package com.bookticket.payment_service.service;

import com.bookticket.payment_service.configuration.StripeConfig;
import com.bookticket.payment_service.entity.Payment;
import com.bookticket.payment_service.enums.PaymentStatus;
import com.bookticket.payment_service.repository.PaymentRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpiredPaymentService {

    private final PaymentRepository paymentRepository;
    private final StripeConfig stripeConfig;

    /**
     * Scheduled task to check for expired payment sessions
     * Runs every minute to check for expired sessions
     */
    @Scheduled(fixedRate = 60000) // Run every minute
    @Transactional
    public void expireOldSessions() {
        log.info("Starting session expiration check at: {}", Instant.now());

        // Find all pending payments
        List<Payment> pendingPayments = paymentRepository.findByPaymentStatus(PaymentStatus.PENDING);
        if (pendingPayments.isEmpty()) {
            log.info("No pending payments found. Exiting session expiration check.");
            return;
        }

        for (Payment payment : pendingPayments) {
            try {
                // Skip if no transaction ID or created time
                if (payment.getTransactionId() == null || payment.getTransactionId().isBlank() ||
                        payment.getCreatedAt() == null) {
                    continue;
                }

                // Check if payment is older than 4 minutes
                if (isSessionExpired(payment.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant())) {
                    log.info("Expiring session - Transaction ID: {}, Booking ID: {}",
                            payment.getTransactionId(), payment.getBookingId());

                    // Expire the session in Stripe
                    expireStripeSession(payment.getTransactionId());

                    // Update payment status
                    payment.setPaymentStatus(PaymentStatus.FAILED);
                    payment.setPaymentGatewayResponse(
                            "Payment session expired after " + stripeConfig.getCheckoutSessionExpiryMinutes() + " minutes");
                    paymentRepository.save(payment);

                    log.info("Successfully expired session - Transaction ID: {}, Booking ID: {}",
                            payment.getTransactionId(), payment.getBookingId());
                }

            } catch (Exception e) {
                log.error("Error expiring session for payment {}: {}",
                        payment.getTransactionId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Check if the given instant is older than K minutes
     */
    private boolean isSessionExpired(Instant instant) {
        return instant.plus(stripeConfig.getCheckoutSessionExpiryMinutes(), ChronoUnit.MINUTES).isBefore(Instant.now());
    }

    /**
     * Call Stripe API to expire a checkout session
     */
    private void expireStripeSession(String sessionId) throws StripeException {
        log.info("Expiring Stripe session: {}", sessionId);
        try {
            Session session = Session.retrieve(sessionId);
            session.expire();
        } catch (StripeException e) {
            log.error("Failed to expire Stripe session {}: {}", sessionId, e.getMessage(), e);
            throw new RuntimeException(e);
        }
        log.info("Successfully expired Stripe session: {}", sessionId);
    }
}
