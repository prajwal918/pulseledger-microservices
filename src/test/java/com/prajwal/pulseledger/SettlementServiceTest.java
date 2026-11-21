package com.prajwal.pulseledger;

import com.prajwal.pulseledger.domain.Account;
import com.prajwal.pulseledger.domain.LedgerEntry;
import com.prajwal.pulseledger.repository.AccountRepository;
import com.prajwal.pulseledger.repository.LedgerEntryRepository;
import com.prajwal.pulseledger.repository.OutboxEventRepository;
import com.prajwal.pulseledger.service.SettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock
    private AccountRepository accountRepo;
    @Mock
    private LedgerEntryRepository ledgerRepo;
    @Mock
    private OutboxEventRepository outboxRepo;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private SettlementService settlementService;

    @BeforeEach
    void setUp() {
        settlementService = new SettlementService(accountRepo, ledgerRepo, outboxRepo, redisTemplate);
    }

    @Test
    void executeSettlement_SuccessfulDoubleEntryTransfer() {
        Account accA = new Account("ACC-001", new BigDecimal("1000.00"));
        Account accB = new Account("ACC-002", new BigDecimal("500.00"));

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(accountRepo.findByAccountNumber("ACC-001")).thenReturn(Optional.of(accA));
        when(accountRepo.findByAccountNumber("ACC-002")).thenReturn(Optional.of(accB));
        when(ledgerRepo.save(any(LedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BigDecimal transferAmt = new BigDecimal("250.00");
        LedgerEntry entry = settlementService.executeSettlement("TX-9988", "ACC-001", "ACC-002", transferAmt);

        assertNotNull(entry);
        assertEquals(new BigDecimal("750.00"), accA.getBalance());
        assertEquals(new BigDecimal("750.00"), accB.getBalance());
        verify(accountRepo, times(1)).save(accA);
        verify(accountRepo, times(1)).save(accB);
        verify(ledgerRepo, times(1)).save(any(LedgerEntry.class));
        verify(outboxRepo, times(1)).save(any());
    }

    @Test
    void executeSettlement_FailsOnInsufficientFunds() {
        Account accA = new Account("ACC-001", new BigDecimal("50.00"));
        Account accB = new Account("ACC-002", new BigDecimal("500.00"));

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(accountRepo.findByAccountNumber("ACC-001")).thenReturn(Optional.of(accA));
        when(accountRepo.findByAccountNumber("ACC-002")).thenReturn(Optional.of(accB));

        assertThrows(IllegalStateException.class, () ->
                settlementService.executeSettlement("TX-FAIL", "ACC-001", "ACC-002", new BigDecimal("100.00"))
        );
        verify(ledgerRepo, never()).save(any());
    }
}
