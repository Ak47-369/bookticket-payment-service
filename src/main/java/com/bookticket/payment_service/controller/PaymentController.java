package com.bookticket.payment_service.controller;

import com.bookticket.payment_service.dto.PaymentResponse;
import com.bookticket.payment_service.dto.CheckoutSessionRequest;
import com.bookticket.payment_service.dto.CheckoutSessionResponse;
import com.bookticket.payment_service.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal/payments")
@RequiredArgsConstructor
@Tag(name = "Payment Controller", description = "APIs for handling payment processing with Stripe")
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(
            summary = "Create Stripe Checkout Session",
            description = "Creates a secure Stripe Checkout Session for a booking. This is intended for backend-to-backend communication (e.g., called by the Booking Service). It returns a payment URL that the end-user should be redirected to.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Checkout session created successfully",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = CheckoutSessionResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request data provided",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "500", description = "Internal server error or error communicating with Stripe",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "503", description = "Service unavailable",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "504", description = "Gateway timeout",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "429", description = "Too many requests",
                            content = @Content(mediaType = "application/json"))
            }
    )
    @PostMapping("/checkout/create")
    public ResponseEntity<CheckoutSessionResponse> createCheckoutSession(
            @Valid @RequestBody CheckoutSessionRequest request) {
        CheckoutSessionResponse response = paymentService.createCheckoutSession(request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Verify Checkout Session",
            description = "Verifies the status of a Stripe Checkout Session after the user has completed the payment flow. This is called by the Booking Service to confirm payment success or failure.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Payment status verified successfully",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Session ID not found",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "500", description = "Internal server error or error communicating with Stripe",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "503", description = "Service unavailable",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "504", description = "Gateway timeout",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "429", description = "Too many requests",
                            content = @Content(mediaType = "application/json"))
            }
    )
    @GetMapping("/checkout/verify/{sessionId}")
    public ResponseEntity<PaymentResponse> verifyCheckoutSession(
            @Parameter(description = "The session ID provided by Stripe", required = true)
            @PathVariable String sessionId) {
        PaymentResponse response = paymentService.verifyCheckoutSession(sessionId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get Payment Status",
            description = "Retrieves the current status of a payment from the local database using the transaction/session ID.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Payment status retrieved successfully",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Transaction ID not found",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "500", description = "Internal server error",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "503", description = "Service unavailable",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "504", description = "Gateway timeout",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "429", description = "Too many requests",
                            content = @Content(mediaType = "application/json"))
            }
    )
    @GetMapping("/status/{transactionId}")
    public ResponseEntity<PaymentResponse> getPaymentStatus(
            @Parameter(description = "The transaction/session ID of the payment", required = true)
            @PathVariable String transactionId) {
        PaymentResponse response = paymentService.getPaymentStatus(transactionId);
        return ResponseEntity.ok(response);
    }
}
