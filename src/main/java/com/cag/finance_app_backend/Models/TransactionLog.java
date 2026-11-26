package com.cag.finance_app_backend.Models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "transaction_logs")
public class TransactionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long transactionId;

    private String actionByEmail; // The system identifier
    private String actionByName;  // For readable UI logs ("verifier_name")

    @Column(nullable = false)
    private String actionType; 
    // Examples: "SUBMITTED", "EDITED", "VERIFIED", "ALERT_RAISED", "COMPLETED", "DISPUTE_RAISED", "PROOF_UPDATED"

    @Column(columnDefinition = "TEXT")
    private String details; 
    // This will hold the custom messages or the "Old Value -> New Value" strings

    @Column(nullable = false)
    private LocalDateTime timestamp;

}