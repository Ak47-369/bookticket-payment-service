package com.bookticket.payment_service.service.impl;

import com.bookticket.payment_service.configuration.StripeConfig;
import com.bookticket.payment_service.dto.*;
import com.bookticket.payment_service.entity.Payment;
import com.bookticket.payment_service.enums.PaymentStatus;
import com.bookticket.payment_service.exception.PaymentNotFoundException;
import com.bookticket.payment_service.exception.PaymentProcessingException;
import com.bookticket.payment_service.repository.PaymentRepository;
import com.bookticket.payment_service.service.PaymentService;
import com.stripe.exception.*;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripePaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final StripeConfig stripeConfig;

    /**
     * Create Stripe Checkout Session - SECURE & BACKEND-FOCUSED
     *
     * How it works:
     * 1. Backend creates a Checkout Session
     * 2. Returns a URL to the client
     * 3. Client opens URL in browser (Stripe's hosted page)
     * 4. User enters card details on Stripe's secure page
     * 5. Stripe redirects back to success/cancel URL
     * 6. Backend verifies payment status
     *
     * Advantages:
     * - 100% PCI Compliant (card details never touch your server)
     */
    @Override
    @Transactional
    public CheckoutSessionResponse createCheckoutSession(CheckoutSessionRequest request) {
        log.info("Creating Checkout Session for booking ID: {}, amount: {}",
                request.bookingId(), request.amount());

        try {
            // Convert amount to cents (Stripe requires smallest currency unit)
            long amountInCents = convertToCents(request.amount());

            // Determine success and cancel URLs
            String successUrl = request.successUrl() != null && !request.successUrl().isBlank()
                    ? request.successUrl()
                    : stripeConfig.getSuccessUrl();
            String cancelUrl = request.cancelUrl() != null && !request.cancelUrl().isBlank()
                    ? request.cancelUrl()
                    : stripeConfig.getCancelUrl();

            // Create metadata to track booking and user
            HashMap<String, String> metadata = new HashMap<>();
            metadata.put("bookingId", String.valueOf(request.bookingId()));
            metadata.put("userId", String.valueOf(request.userId()));

            // Create Checkout Session parameters
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setPriceData(
                                            SessionCreateParams.LineItem.PriceData.builder()
                                                    .setCurrency("inr")
                                                    .setUnitAmount(amountInCents)
                                                    .setProductData(
                                                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                    .setName("Booking Payment")
                                                                    .setDescription("Payment for booking ID: " + request.bookingId())
                                                                    .build()
                                                    )
                                                    .build()
                                    )
                                    .setQuantity(1L)
                                    .build()
                    )
                    .putAllMetadata(metadata)
                    .setExpiresAt(System.currentTimeMillis() / 1000 + 18000) // 30 minutes expiry
                    .build();

            // Create Checkout Session via Stripe API
            Session session = Session.create(params);

            log.info("Checkout Session created successfully: {}, URL: {}",
                    session.getId(), session.getUrl());

            // Save initial payment record
            Payment payment = Payment.builder()
                    .bookingId(request.bookingId())
                    .userId(request.userId())
                    .amount(request.amount())
                    .paymentMethod("Stripe_Checkout_Session")
                    .paymentStatus(PaymentStatus.PENDING)
                    .transactionId(session.getId())
                    .paymentGatewayResponse("Checkout Session created: " + session.getId())
                    .build();
            paymentRepository.save(payment);

            log.info("Payment record saved with Checkout Session ID: {}", session.getId());

            return new CheckoutSessionResponse(
                    session.getId(),
                    session.getUrl(),
                    request.bookingId(),
                    request.amount(),
                    "pending",
                    "Checkout session created. Go to the provided paymentUrl to complete payment.",
                    session.getExpiresAt()
            );

        } catch (InvalidRequestException e) {
            log.error("Invalid request creating Checkout Session for booking ID {}: Param: {}, Message: {}",
                    request.bookingId(), e.getParam(), e.getMessage(), e);
            throw new PaymentProcessingException(
                    "Invalid payment request: " + e.getMessage(), e);

        } catch (AuthenticationException e) {
            log.error("Stripe authentication failed creating Checkout Session for booking ID {}: {}",
                    request.bookingId(), e.getMessage(), e);
            throw new PaymentProcessingException(
                    "Payment service authentication error. Please contact support.", e);

        } catch (RateLimitException e) {
            log.error("Rate limit exceeded creating Checkout Session for booking ID {}: {}",
                    request.bookingId(), e.getMessage(), e);
            throw new PaymentProcessingException(
                    "Too many requests. Please try again in a few moments.", e);

        } catch (StripeException e) {
            log.error("Stripe error creating Checkout Session for booking ID {}: {}",
                    request.bookingId(), e.getMessage(), e);
            throw new PaymentProcessingException(
                    "Failed to create checkout session. Please try again.", e);

        } catch (Exception e) {
            log.error("Unexpected error creating Checkout Session for booking ID {}: {}",
                    request.bookingId(), e.getMessage(), e);
            throw new PaymentProcessingException(
                    "An unexpected error occurred. Please try again later.", e);
        }
    }

    /**
     * Verify Checkout Session and update payment status
     * Call this after user completes payment on Stripe's hosted page
     */
    @Override
    @Transactional
    public PaymentResponse verifyCheckoutSession(String sessionId) {
        log.info("Verifying Checkout Session: {}", sessionId);

        try {
            // Retrieve Checkout Session from Stripe with expanded payment_intent
            // IMPORTANT: We need to expand payment_intent to get the actual payment status
            HashMap<String, Object> params = new HashMap<>();
            params.put("expand", List.of("payment_intent"));

            Session session = Session.retrieve(sessionId, params, null);

            // Get the PaymentIntent object (now expanded)
            PaymentIntent paymentIntent = null;
            String paymentIntentId = null;
            String paymentIntentStatus = null;

            if (session.getPaymentIntentObject() != null) {
                paymentIntent = session.getPaymentIntentObject();
                paymentIntentId = paymentIntent.getId();
                paymentIntentStatus = paymentIntent.getStatus();
                log.info("PaymentIntent expanded - ID: {}, Status: {}", paymentIntentId, paymentIntentStatus);
            } else if (session.getPaymentIntent() != null) {
                paymentIntentId = session.getPaymentIntent();
                log.warn("PaymentIntent not expanded, only ID available: {}", paymentIntentId);
            }

            log.info("Checkout Session retrieved: {}, payment_status: {}, payment_intent: {}, session status: {}",
                    session.getId(), session.getPaymentStatus(), paymentIntentId, session.getStatus());

            // Find payment record by session ID
            Payment payment = paymentRepository.findByTransactionId(sessionId)
                    .orElseThrow(() -> new PaymentNotFoundException(
                            "Payment not found for session ID: " + sessionId));

            // Check if session is expired
            if ("expired".equalsIgnoreCase(session.getStatus())) {
                log.warn("Checkout Session expired: {}", sessionId);
                payment.setPaymentStatus(PaymentStatus.FAILED);
                payment.setPaymentGatewayResponse(
                        String.format("Session expired: %s", session.getId())
                );
                paymentRepository.save(payment);
                return buildPaymentResponse(payment, "Checkout session expired. Please create a new payment.");
            }

            // Log PaymentIntent failures (card declined, etc.) but don't fail the session yet
            if (paymentIntent != null && "requires_payment_method".equals(paymentIntentStatus)) {
                String failureReason = paymentIntent.getLastPaymentError() != null
                        ? paymentIntent.getLastPaymentError().getMessage()
                        : "Unknown error";
                log.warn("Payment attempt failed for session {}: PaymentIntent status: {}, Reason: {}",
                        sessionId, paymentIntentStatus, failureReason);
                log.info("Customer can retry with another payment method on the same session");
                // Don't update payment status yet - customer can still retry
            }

            // Update payment status based on Checkout Session payment_status
            PaymentStatus newStatus = mapCheckoutSessionStatus(session.getPaymentStatus());
            if(newStatus == PaymentStatus.COMPLETED) {
                payment.setPaymentIntentId(paymentIntentId);
                String gatewayResponse = String.format("Session: %s, Status: %s, PaymentIntent: %s",
                        session.getId(), session.getPaymentStatus(), paymentIntentId);
                if (paymentIntent != null && paymentIntent.getLastPaymentError() != null) {
                    gatewayResponse += String.format(", Last Error: %s",
                            paymentIntent.getLastPaymentError().getMessage());
                }
                payment.setPaymentGatewayResponse(gatewayResponse);

                log.info("Payment status updated for session {}: {} -> {}",
                        sessionId, payment.getPaymentStatus(), newStatus);
                payment.setPaymentStatus(newStatus);
                paymentRepository.save(payment);
                return buildPaymentResponse(payment, "Payment verification successful");
            }
            return buildPaymentResponse(payment, "Payment is Pending. Please try again.");

        } catch (PaymentNotFoundException e) {
            throw e;

        } catch (InvalidRequestException e) {
            log.error("Invalid session ID {}: {}", sessionId, e.getMessage(), e);
            throw new PaymentProcessingException(
                    "Invalid session ID: " + e.getMessage(), e);

        } catch (AuthenticationException e) {
            log.error("Stripe authentication failed verifying session {}: {}",
                    sessionId, e.getMessage(), e);
            throw new PaymentProcessingException(
                    "Payment service authentication error. Please contact support.", e);

        } catch (RateLimitException e) {
            log.error("Rate limit exceeded verifying session {}: {}", sessionId, e.getMessage(), e);
            throw new PaymentProcessingException(
                    "Too many requests. Please try again in a few moments.", e);

        } catch (StripeException e) {
            log.error("Stripe error verifying session {}: {}", sessionId, e.getMessage(), e);
            throw new PaymentProcessingException(
                    "Failed to verify checkout session. Please try again.", e);

        } catch (Exception e) {
            log.error("Unexpected error verifying session {}: {}", sessionId, e.getMessage(), e);
            throw new PaymentProcessingException(
                    "An unexpected error occurred. Please try again later.", e);
        }
    }

    /**
     * Convert amount to cents (smallest currency unit for Stripe)
     * @param amount Amount in rupees
     * @return Amount in paise (cents)
     */
    private long convertToCents(Double amount) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        return Math.round(amount * 100);
    }

    /**
     * Map Stripe Checkout Session payment_status to internal PaymentStatus enum
     * @param paymentStatus Stripe Checkout Session payment_status
     * @return Internal PaymentStatus enum
     */
    private PaymentStatus mapCheckoutSessionStatus(String paymentStatus) {
        if (paymentStatus == null) {
            return PaymentStatus.PENDING;
        }

        return switch (paymentStatus.toLowerCase()) {
            case "paid" -> PaymentStatus.COMPLETED;
            case "unpaid" -> PaymentStatus.PENDING;
            case "no_payment_required" -> PaymentStatus.COMPLETED;
            default -> {
                log.warn("Unknown Checkout Session payment_status: {}, defaulting to PENDING", paymentStatus);
                yield PaymentStatus.PENDING;
            }
        };
    }

    /**
     * Save failed payment record to database
     * @param paymentRequest Original payment request
     * @param errorMessage Error message from exception
     * @param errorCode Error code or description
     * @return Saved payment entity or null if save fails
     */

    /**
     * Get user-friendly message for card decline codes
     * @param declineCode Stripe decline code
     * @return User-friendly message
     */
    /*
    private String getCardDeclineMessage(String declineCode) {
        if (declineCode == null) {
            return "Please try a different payment method.";
        }

        return switch (declineCode.toLowerCase()) {
            case "insufficient_funds" -> "Your card has insufficient funds. Please use a different card.";
            case "lost_card" -> "This card has been reported lost. Please use a different card.";
            case "stolen_card" -> "This card has been reported stolen. Please use a different card.";
            case "expired_card" -> "Your card has expired. Please use a different card.";
            case "incorrect_cvc" -> "The card security code is incorrect. Please check and try again.";
            case "processing_error" -> "An error occurred while processing your card. Please try again.";
            case "incorrect_number" -> "The card number is incorrect. Please check and try again.";
            case "card_not_supported" -> "This type of card is not supported. Please use a different card.";
            case "card_velocity_exceeded" -> "You've exceeded the number of allowed transactions. Please try again later.";
            case "currency_not_supported" -> "Your card doesn't support this currency. Please use a different card.";
            case "duplicate_transaction" -> "A transaction with identical details was recently submitted. Please wait before retrying.";
            case "fraudulent" -> "This transaction has been flagged as potentially fraudulent. Please contact your bank.";
            case "generic_decline" -> "Your card was declined. Please contact your bank for more information.";
            case "invalid_account" -> "The card account is invalid. Please use a different card.";
            case "invalid_amount" -> "The payment amount is invalid. Please try again.";
            case "invalid_cvc" -> "The card security code is invalid. Please check and try again.";
            case "invalid_expiry_month" -> "The card expiration month is invalid. Please check and try again.";
            case "invalid_expiry_year" -> "The card expiration year is invalid. Please check and try again.";
            case "invalid_number" -> "The card number is invalid. Please check and try again.";
            case "issuer_not_available" -> "Your card issuer is temporarily unavailable. Please try again later.";
            case "new_account_information_available" -> "Your card information has been updated. Please use the new card details.";
            case "no_action_taken" -> "The transaction was not processed. Please try again.";
            case "not_permitted" -> "This transaction is not permitted on your card. Please use a different card.";
            case "pickup_card" -> "Your card cannot be used. Please contact your bank.";
            case "pin_try_exceeded" -> "You've exceeded the number of PIN attempts. Please contact your bank.";
            case "reenter_transaction" -> "The transaction could not be processed. Please try again.";
            case "restricted_card" -> "Your card has restrictions. Please contact your bank or use a different card.";
            case "revocation_of_all_authorizations" -> "Your card authorizations have been revoked. Please use a different card.";
            case "revocation_of_authorization" -> "This authorization has been revoked. Please use a different card.";
            case "security_violation" -> "A security violation was detected. Please contact your bank.";
            case "service_not_allowed" -> "This service is not allowed on your card. Please use a different card.";
            case "stop_payment_order" -> "A stop payment order is in place. Please contact your bank.";
            case "testmode_decline" -> "This is a test card decline (test mode only).";
            case "transaction_not_allowed" -> "This transaction is not allowed. Please use a different card.";
            case "try_again_later" -> "The transaction could not be processed. Please try again later.";
            case "withdrawal_count_limit_exceeded" -> "You've exceeded the withdrawal limit. Please try again later.";
            default -> "Your card was declined. Please try a different payment method or contact your bank.";
        };
    }
    */
    private PaymentResponse buildPaymentResponse(Payment payment, String message) {
        return new PaymentResponse(
                payment.getId(),
                payment.getBookingId(),
                payment.getPaymentStatus().name(),
                payment.getTransactionId(),
                payment.getAmount(),
                message
        );
    }
}
