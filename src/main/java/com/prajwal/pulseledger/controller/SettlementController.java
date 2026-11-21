package com.prajwal.pulseledger.controller;

import com.prajwal.pulseledger.domain.Account;
import com.prajwal.pulseledger.domain.LedgerEntry;
import com.prajwal.pulseledger.repository.AccountRepository;
import com.prajwal.pulseledger.service.SettlementService;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ledger")
public class SettlementController {

    private final SettlementService settlementService;
    private final AccountRepository accountRepo;

    public record TransferRequest(
            @NotBlank String sourceAccount,
            @NotBlank String destinationAccount,
            @NotNull @DecimalMin("0.01") BigDecimal amount
    ) {}

    public SettlementController(SettlementService settlementService, AccountRepository accountRepo) {
        this.settlementService = settlementService;
        this.accountRepo = accountRepo;
    }

    @PostMapping("/transfers")
    public ResponseEntity<?> transfer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody TransferRequest req) {
        LedgerEntry entry = settlementService.executeSettlement(
                idempotencyKey, req.sourceAccount(), req.destinationAccount(), req.amount()
        );
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "transactionId", entry.getTransactionId(),
                "timestamp", entry.getCreatedAt()
        ));
    }

    @GetMapping("/accounts/{accountNumber}")
    public ResponseEntity<Account> getAccount(@PathVariable String accountNumber) {
        return accountRepo.findByAccountNumber(accountNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
