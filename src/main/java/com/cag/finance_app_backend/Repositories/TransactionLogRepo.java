package com.cag.finance_app_backend.Repositories;

import com.cag.finance_app_backend.Models.TransactionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TransactionLogRepo extends JpaRepository<TransactionLog, Long> {
    
   
    List<TransactionLog> findByTransactionIdOrderByTimestampDesc(Long transactionId);
    
}