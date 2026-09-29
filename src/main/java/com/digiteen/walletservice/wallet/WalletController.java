package com.digiteen.walletservice.wallet;

import com.digiteen.walletservice.dto.AmountRequest;
import com.digiteen.walletservice.dto.TransactionResponse;
import com.digiteen.walletservice.dto.TransferRequest;
import com.digiteen.walletservice.dto.WalletResponse;
import com.digiteen.walletservice.transaction.TransactionStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallet")
@Tag(name = "Wallet", description = "Owner-only wallet operations")
public class WalletController {
    private final WalletApplicationService service;

    public WalletController(WalletApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Get balance", description = "Returns or creates the authenticated user's wallet.")
    WalletResponse wallet(@AuthenticationPrincipal Jwt jwt) {
        return service.getOrCreateWallet(userId(jwt));
    }

    @GetMapping("/transactions")
    @Operation(summary = "Get transaction history")
    Page<TransactionResponse> history(@AuthenticationPrincipal Jwt jwt,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        return service.history(userId(jwt), PageRequest.of(Math.max(page, 0), safeSize));
    }

    @PostMapping("/deposits")
    @Operation(summary = "Deposit")
    ResponseEntity<TransactionResponse> deposit(@AuthenticationPrincipal Jwt jwt,
            @Parameter(required = true) @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody AmountRequest request) {
        return response(service.deposit(userId(jwt), request.amount(), key));
    }

    @PostMapping("/withdrawals")
    @Operation(summary = "Withdraw", description = "Never allows a negative balance.")
    ResponseEntity<TransactionResponse> withdraw(@AuthenticationPrincipal Jwt jwt,
            @Parameter(required = true) @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody AmountRequest request) {
        return response(service.withdraw(userId(jwt), request.amount(), key));
    }

    @PostMapping("/transfers")
    @Operation(summary = "Transfer", description = "Atomically debits the owner and credits the target wallet.")
    ResponseEntity<TransactionResponse> transfer(@AuthenticationPrincipal Jwt jwt,
            @Parameter(required = true) @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody TransferRequest request) {
        return response(service.transfer(userId(jwt), request.targetWalletId(), request.amount(), key));
    }

    private ResponseEntity<TransactionResponse> response(TransactionResponse response) {
        return ResponseEntity.status(response.status() == TransactionStatus.SUCCEEDED
                ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
