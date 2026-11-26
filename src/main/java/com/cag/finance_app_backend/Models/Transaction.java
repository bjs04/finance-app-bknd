package com.cag.finance_app_backend.Models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

import com.cag.finance_app_backend.Enums.TransactionStatus;

@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "person_name")
    private String personName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "transaction_date")
    private LocalDate transactionDate; 

    // NEW COLUMN: Maps to your React "Bill NA" toggle
    @Column(name = "has_bill", nullable = false)
    private boolean hasBill = true; 

    @Column(name = "record_type", length = 10)
    private String recordType; // P, D, NA

    @Column(name = "image_url", length = 1000)
    private String imageUrl; // This will hold the Cloudinary URL

    @Column(name = "is_submitted", nullable = false) 
    private boolean physicalBillSubmitted = false;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column(name = "payment_proof_url", length = 1000)
    private String paymentProofUrl;

    public String getFormattedId() {
        if (this.id != null && this.transactionDate != null) {
            int year = this.transactionDate.getYear();
            int month = this.transactionDate.getMonthValue();

            // If the month is Jan, Feb, or March (before April), 
            // it belongs to the PREVIOUS financial year.
            if (month < 4) {
                year = year - 1; 
            }

            // Extract the last two digits of the year (e.g., 2026 -> 26)
            int shortYear = year % 100;
            
            return "TR" + shortYear + "_" + this.id;
        }
        
        // Safe fallback just in case the date is missing
        return "TR_" + this.id; 
    }
}