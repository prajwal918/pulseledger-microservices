package com.prajwal.pulseledger.service;

import com.prajwal.pulseledger.domain.Account;
import com.prajwal.pulseledger.domain.LedgerEntry;
import com.prajwal.pulseledger.domain.OutboxEvent;
import com.prajwal.pulseledger.repository.AccountRepository;
import com.prajwal.pulseledger.repository.LedgerEntryRepository;
import com.prajwal.pulseledger.repository.OutboxEventRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;

@Service
public class SettlementService {

    private final AccountRepository accountRepo;
    private final LedgerEntryRepository ledgerRepo;
    private final OutboxEventRepository outboxRepo;
    private final StringRedisTemplate redisTemplate;

    public SettlementService(AccountRepository accountRepo,
                             LedgerEntryRepository ledgerRepo,
                             OutboxEventRepository outboxRepo,
                             StringRedisTemplate redisTemplate) {
        this.accountRepo = accountRepo;
        this.ledgerRepo = ledgerRepo;
        this.outboxRepo = outboxRepo;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public LedgerEntry executeSettlement(String idempotencyKey, String fromAccNum, String toAccNum, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be strictly positive");
        }

        // Redis-backed idempotency check (24 hour TTL)
        String redisKey = "idemp:" + idempotencyKey;
        if (redisTemplate != null) {
            Boolean isNew = redisTemplate.opsForValue().setIfAbsent(redisKey, "PROCESSING", Duration.ofHours(24));
            if (Boolean.FALSE.equals(isNew)) {
                throw new IllegalStateException("Duplicate transaction detected for idempotency key: " + idempotencyKey);
            }
        }

        Account source = accountRepo.findByAccountNumber(fromAccNum)
                .orElseThrow(() -> new IllegalArgumentException("Source account not found: " + fromAccNum));
        Account target = accountRepo.findByAccountNumber(toAccNum)
                .orElseThrow(() -> new IllegalArgumentException("Destination account not found: " + toAccNum));

        // Optimistic locking via @Version guarantees race condition defense
        source.debit(amount);
        target.credit(amount);

        accountRepo.save(source);
        accountRepo.save(target);

        // Double-entry record creation
        LedgerEntry entry = new LedgerEntry(idempotencyKey, fromAccNum, toAccNum, amount);
        LedgerEntry savedEntry = ledgerRepo.save(entry);

        // Transactional outbox event creation for asynchronous audit reconciliation
        String payload = String.format("{\"txId\":\"%s\",\"from\":\"%s\",\"to\":\"%s\",\"amount\":%s}",
                idempotencyKey, fromAccNum, toAccNum, amount);
        OutboxEvent event = new OutboxEvent("LEDGER_TRANSFER", idempotencyKey, "FUNDS_TRANSFERRED", payload);
        outboxRepo.save(event);

        if (redisTemplate != null) {
            redisTemplate.opsForValue().set(redisKey, "COMPLETED", Duration.ofHours(24));
        }

        return savedEntry;
    }
}
