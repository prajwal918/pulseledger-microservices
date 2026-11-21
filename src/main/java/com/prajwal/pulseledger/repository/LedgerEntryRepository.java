package com.prajwal.pulseledger.repository;

import com.prajwal.pulseledger.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    List<LedgerEntry> findBySourceAccountOrDestinationAccount(String source, String destination);
}
