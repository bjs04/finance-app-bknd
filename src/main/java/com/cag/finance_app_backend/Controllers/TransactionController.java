package com.cag.finance_app_backend.Controllers;

import com.cag.finance_app_backend.Models.Transaction;
import com.cag.finance_app_backend.Models.TransactionLog;
import com.cag.finance_app_backend.Services.TransactionService;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;

    }

    
    @GetMapping("/transactions")
    public ResponseEntity<?> getTransactions(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size, 
        @RequestParam(required = false) String search,
        Authentication authentication
    ) {
        // 1. If the code reaches here, the token is verified and the user is approved!
        // We get the email directly from the Security Context (the Filter put it here).
        String email = authentication.getName(); 

        // 2. Pass the email directly to the service. We don't even need the role here anymore!
        Page<Transaction> transactions = transactionService.getTransactionsForUser(email,page, size, search);
        
        return ResponseEntity.ok(transactions);
    }

    @PreAuthorize("hasAuthority('UPLOAD_TX')")
    @PostMapping("/transactions/add")
    public ResponseEntity<?> addTransaction(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam("amount") Double amount,
            @RequestParam("description") String description,
            @RequestParam("hasBill") Boolean hasBill,
            @RequestParam("billType") String billType,
            @RequestParam(value = "remarks", required = false) String remarks,
            @RequestParam("personName") String personName,
            Authentication authentication
    ) {
        try {
            // 1. Get the user's email directly from the Security Context
            String email = authentication.getName();
            //System.out.println("Inside Transaction Controller");
            // 2. Pass EVERYTHING down to the Service layer to do the heavy lifting
            Transaction tx = transactionService.createTransaction(
                    file, amount, description, hasBill, billType, remarks, personName, email
            );

            // 3. Return the saved object back to React
            return ResponseEntity.ok(java.util.Collections.singletonMap("message", "Transaction " + tx.getFormattedId() + " ADDED successfully!"));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error saving transaction: " + e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('VERIFY_TX')")
    @PutMapping("/transactions/{id}/verify")
    public ResponseEntity<?> verifyTransaction(@PathVariable Long id, Principal principal) {
        try {
            String email = principal.getName();
            Transaction tx = transactionService.verifyTransaction(id, email);
            return ResponseEntity.ok(Map.of("message", "Transaction " + tx.getFormattedId() + " VERIFIED successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('VERIFY_TX')")
    @PutMapping("/transactions/{id}/alert")
    public ResponseEntity<?> raiseAlert(
            @PathVariable Long id, 
            @RequestBody Map<String, String> payload,
            Principal principal
    ) {
        try {
            String email = principal.getName();
            String remarks = payload.get("remarks");
            
            // Safety check in case the frontend sends an empty string
            if (remarks == null || remarks.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Remarks are required to raise an alert.");
            }

            Transaction tx = transactionService.raiseAlertTransaction(id, remarks, email);
            return ResponseEntity.ok(Map.of("message", "Alert set successfully for Transaction: " + tx.getFormattedId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('FUND_MANAGER')") 
    @PostMapping("/transactions/{id}/pay")
    public ResponseEntity<?> markAsPaid(
            @PathVariable Long id, 
            @RequestParam("file") MultipartFile file,
            Principal principal
    ) {
        try {
            String email = principal.getName();
            Transaction tx = transactionService.payTransaction(id, file, email);
            return ResponseEntity.ok(Map.of("message", "Transaction " + tx.getFormattedId() + " COMPLETED successfully."));
        } catch (Exception e) {
            // Returns a 400 Bad Request with the exact error message from the service layer
            return ResponseEntity.badRequest().body(Map.of("err", e.getMessage()));
        }
    }

    // 4. THE EDIT ROUTE (Only works PRE-VERIFICATION)
    @PreAuthorize("hasAuthority('UPLOAD_TX')")
    @PutMapping("/transactions/{id}")
    public ResponseEntity<?> updateTransaction(
            @PathVariable Long id,
            @RequestParam(value = "remarks", required = false) String remarks,
            @RequestParam(value = "amount", required = false) Double amount,
            @RequestParam(value = "recordType", required = false) String recordType,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "file", required = false) MultipartFile file,
            Principal principal
    ) {
        try {
            
            String email = principal.getName();
            Transaction tx = transactionService.updateTransactionDetails(id, remarks, amount, recordType, description, file, email);
            return ResponseEntity.ok(Map.of(
                "message", "Transaction " + tx.getFormattedId() + " updated successfully!"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PreAuthorize("hasAuthority('VERIFY_TX') or hasAuthority('FUND_MANAGER') or hasAuthority('UPLOAD_TX')")
    @GetMapping("/transactions/{id}/logs")
    public ResponseEntity<List<TransactionLog>> getTransactionLogs(@PathVariable Long id, Principal principal) {
        String loggedInEmail = principal.getName();
        List<TransactionLog> logs = transactionService.findTxLogs(id, loggedInEmail);
        return ResponseEntity.ok(logs);
    }

    @PreAuthorize("hasAuthority('UPLOAD_TX')")
    @PostMapping("/transactions/{id}/issue")
    public ResponseEntity<?> raiseIssue(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload,
            Principal principal
    ) {
        // Extract the remarks from the { remarks: "..." } JSON body
        String remarks = payload.get("remarks");
        
        if (remarks == null || remarks.trim().isEmpty()) {
            // If they somehow bypass the frontend disabled button, block it here
            return ResponseEntity.badRequest().body(Map.of("error", "Please provide a reason for the issue."));
        }

        String loggedInEmail = principal.getName();
        
        // Call the service layer (Happy path only; exceptions caught by GlobalExceptionHandler)
        transactionService.raiseIssue(id, remarks, loggedInEmail);
        
        // Return JSON so React can read response.data.message
        return ResponseEntity.ok(Map.of(
            "message", "Issue raised successfully and logged in the system."
        ));
    }
}