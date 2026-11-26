package com.cag.finance_app_backend.Services;

import com.cag.finance_app_backend.Enums.TransactionStatus;
import com.cag.finance_app_backend.Exceptions.UserNotFoundException;
import com.cag.finance_app_backend.Models.AppUser;
import com.cag.finance_app_backend.Models.Transaction;
import com.cag.finance_app_backend.Models.TransactionLog;
import com.cag.finance_app_backend.Repositories.TransactionLogRepo;
import com.cag.finance_app_backend.Repositories.TransactionRepository;
import com.cag.finance_app_backend.Repositories.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepo;
    private final UserRepository userRepo; 
    private final CloudinaryService cloudinaryService;
    private final TransactionLogRepo logRepo;

    // Constructor injection for repositories and services
    public TransactionService(TransactionRepository transactionRepo, UserRepository userRepo, CloudinaryService cloudinaryService, TransactionLogRepo logRepo) {
        this.transactionRepo = transactionRepo;
        this.userRepo = userRepo;
        this.cloudinaryService = cloudinaryService;
        this.logRepo = logRepo;
    }

   
    public Page<Transaction> getTransactionsForUser(String email, int page, int size, String search) {
        Pageable pageable = PageRequest.of(page, size);
        // 1. Fetch the user to see what role they have
        AppUser user = userRepo.findByEmail(email)
                    .orElseThrow(() -> new UserNotFoundException("User not found"));;

        boolean canViewAll = "ADMIN".equals(user.getRole()) || 
             "FTM".equals(user.getRole()) ||
             user.getPermissions().stream().anyMatch(p -> "VIEW_ALL_TX".equals(p.getName()));
        // 2. Route based on their real role
        
        boolean isSearching = search != null && !search.trim().isEmpty();
        String safeSearch = isSearching ? search.trim() : "";

        if (canViewAll) {
            if (isSearching) {
                // Admin is searching
                return transactionRepo.searchAllTransactions(safeSearch, pageable);
            } else {
                // Admin normal view
                return transactionRepo.findAllByOrderByIdDesc(pageable);
            }
            // Return ALL transactions, sorted newest first
        } else {
            // Return ONLY transactions where the linked AppUser's email matches
            if (isSearching) {
                // Normal user is searching their own records
                return transactionRepo.searchUserTransactions(email, safeSearch, pageable);
            } else {
                // Normal user standard view
                return transactionRepo.findByUserEmailOrderByIdDesc(email, pageable);
            }
        }
    }

    public Transaction createTransaction(
            MultipartFile file, Double amount, String description, 
            Boolean hasBill, String recordType, String remarks, 
            String personName, String email) throws Exception {


        // In TransactionService:
        AppUser currentUser = userRepo.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
            
        // 1. Initialize the file URL as null
        String billUrl = null;

        // 2. If they claim they have a bill, and the file exists, tell CloudinaryService to upload it
        if (hasBill && file != null && !file.isEmpty()) {
            billUrl = cloudinaryService.uploadFile(file);
        }

        // 3. Create and populate the Transaction object
        Transaction transaction = new Transaction();
        transaction.setUser(currentUser);
        transaction.setPersonName(personName);
        transaction.setAmount(BigDecimal.valueOf(amount));
        transaction.setDescription(description);
        transaction.setHasBill(hasBill);
        transaction.setRecordType(recordType); // 'P', 'D', or 'NA'
        transaction.setRemarks(remarks);
        transaction.setImageUrl(billUrl);
        transaction.setTransactionDate(LocalDate.now()); // Set date to today
        transaction.setStatus(TransactionStatus.PENDING);
        
        // 4. Apply specific business rules
        // If physical bill, it starts as NOT submitted
        if ("P".equals(recordType)) {
            transaction.setPhysicalBillSubmitted(false);
        } else {
            // Digital or NA means no physical paper is pending! 
            // We set to true so they don't show up in "Missing Paper" filters later.
            transaction.setPhysicalBillSubmitted(true);  
        }

        // 5. Save to database using the Repository
        Transaction savedTransaction = transactionRepo.save(transaction);
        recordLog(savedTransaction.getId(), email, "SUBMITTED", "Transaction initiated.");
        return savedTransaction;
    }

    public Transaction verifyTransaction(Long id, String email) {
        Transaction transaction = transactionRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        // Optional: Check if it's already verified to prevent double-clicks
        if (transaction.getStatus() == TransactionStatus.VERIFIED) {
            throw new RuntimeException("Transaction is already verified.");
        }

        if ("P".equals(transaction.getRecordType())) {
            transaction.setPhysicalBillSubmitted(true); // NOTE: Ensure this matches your exact entity setter (e.g., setIsSubmitted or setSubmitted)
        }
        transaction.setStatus(TransactionStatus.VERIFIED);
        Transaction verifiedTransaction =  transactionRepo.save(transaction);
        recordLog(verifiedTransaction.getId(), email, "VERIFIED", "Transaction verified.");
        return verifiedTransaction;
    }

    public Transaction raiseAlertTransaction(Long id, String remarks, String email) {
        Transaction transaction = transactionRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        transaction.setStatus(TransactionStatus.ALERT_RAISED);

        
        Transaction alertedTransaction = transactionRepo.save(transaction);
        recordLog(alertedTransaction.getId(), email, "ALERT_RAISED", "Alert details: " + remarks);
        return alertedTransaction;
    }

    public Transaction payTransaction(Long id, MultipartFile file, String email) {
        // 1. Find the transaction
        Transaction transaction = transactionRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        // 2. Strict State Machine Validation
        if (transaction.getStatus() != TransactionStatus.VERIFIED && transaction.getStatus() != TransactionStatus.ISSUE_RAISED) {
            throw new RuntimeException("Transaction must be VERIFIED or have an active ISSUE_RAISED before it can be paid.");
        }

        // 3. Ensure a file was actually provided
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Payment proof document is required to finalize a transaction.");
        }

        try {
            // 4. Upload to Cloudinary (Match this to your actual Cloudinary service method!)
            String fileUrl = cloudinaryService.uploadFile(file); 
            
            // 5. Update the entity
            transaction.setPaymentProofUrl(fileUrl);
            transaction.setStatus(TransactionStatus.COMPLETED);
            
            // 6. Save and return
            Transaction completedTransaction = transactionRepo.save(transaction);
            recordLog(completedTransaction.getId(), email, "PAYMENT_COMPLETED", "Transaction marked as paid.");
            return completedTransaction;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload payment proof: " + e.getMessage());
        }
    }

    public Transaction updateTransactionDetails(Long id, String newRemarks, Double newAmount, String newRecordType, String newDescription, MultipartFile newFile, String loggedInEmail) {
        // 1. Find the transaction
        Transaction transaction = transactionRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        AppUser loggedInUser = userRepo.findByEmail(loggedInEmail)
            .orElseThrow(() -> new RuntimeException("User not found"));

        boolean isOwner = transaction.getUser().getId().equals(loggedInUser.getId());
        boolean isAdmin = ("ADMIN".equals(loggedInUser.getRole()) || "MASTER_ADMIN".equals(loggedInUser.getRole()));
        
        if (!isOwner && !isAdmin) {
            throw new RuntimeException("Security restriction: You can only edit your own transactions.");
        }

        // 2. Strict State Machine Validation (The Lock)
        if (transaction.getStatus() == TransactionStatus.VERIFIED || 
            transaction.getStatus() == TransactionStatus.COMPLETED) {
            throw new RuntimeException("Cannot edit a transaction that has already been verified or completed.");
        }

        // Create our empty list to hold the diffs
        List<String> changes = new ArrayList<>();

        // 3. Update Text Fields (Check if provided AND if they actually changed)
        if (newRemarks != null && !newRemarks.isEmpty() && !newRemarks.equals(transaction.getRemarks())) {
            changes.add("Remarks updated"); // Kept brief so huge text doesn't clutter the log
            transaction.setRemarks(newRemarks);
        }
        
        if (newAmount != null) {
            // Because your entity uses BigDecimal, we must convert and compare safely
            BigDecimal newAmountBD = BigDecimal.valueOf(newAmount);
            if (transaction.getAmount() == null || transaction.getAmount().compareTo(newAmountBD) != 0) {
                changes.add("Amount: ₹" + transaction.getAmount() + " -> ₹" + newAmountBD);
                transaction.setAmount(newAmountBD);
            }
        }
        
        // (Bug fix: Moved this OUTSIDE the recordType block)
        if (newDescription != null && !newDescription.isEmpty() && !newDescription.equals(transaction.getDescription())) {
            changes.add("Description updated");
            transaction.setDescription(newDescription);
        }

        if (newRecordType != null && !newRecordType.isEmpty() && !newRecordType.equals(transaction.getRecordType())) {
            changes.add("Type: " + transaction.getRecordType() + " -> " + newRecordType);
            transaction.setRecordType(newRecordType);
            
            // If they change it to Digital, it's automatically "submitted"
            // If they change it to Physical, it is no longer "submitted" until the verifier gets the paper
            if ("D".equals(newRecordType)) {
                transaction.setPhysicalBillSubmitted(true);
            } else if ("P".equals(newRecordType)) {
                transaction.setPhysicalBillSubmitted(false);
            }
        }

        // 4. Handle Optional File Replacement
        if (newFile != null && !newFile.isEmpty()) {
            try {
                // Upload the new image to Cloudinary
                String newFileUrl = cloudinaryService.uploadFile(newFile); 
                // Overwrite the old URL
                changes.add(transaction.getImageUrl() + " -> " + newFileUrl + " (New URL)");
                transaction.setImageUrl(newFileUrl); 
                System.out.println("New file uploaded and URL updated successfully. URL: " + newFileUrl);
            } catch (Exception e) {
                throw new RuntimeException("Failed to upload new receipt: " + e.getMessage());
            }
        }
        
        // Only log the status change if it actually changed (e.g., from ALERT_RAISED to PENDING)
        if (transaction.getStatus() != TransactionStatus.PENDING) {
            transaction.setStatus(TransactionStatus.PENDING);
        }

        // 5. SAVE FIRST and capture the returned object
        Transaction savedTx = transactionRepo.save(transaction);

        // 6. RECORD LOG (Only if they actually changed something)
        if (!changes.isEmpty()) {
            String details = String.join(" || ", changes);
            recordLog(savedTx.getId(), loggedInEmail, "EDITED", details);
        }

        // 7. RETURN the saved object
        return savedTx;
    }

    private void recordLog(Long txId, String email, String actionType, String details) {
        // Fallback name just in case
        String name = "System"; 
        
        if (email != null) {
            AppUser user = userRepo.findByEmail(email).orElse(null);
            if (user != null) {
                name = user.getName();
            }
        }
        
        TransactionLog log = new TransactionLog(null, txId, email, name, actionType, details, LocalDateTime.now());
        logRepo.save(log);
    }

    public List<TransactionLog> findTxLogs(Long id, String loggedInEmail) {
        Transaction tx = transactionRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        // 2. Find the logged-in user to check their permissions
        AppUser user = userRepo.findByEmail(loggedInEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        boolean hasAdminAccess = "ADMIN".equals(user.getRole()) || 
                             "FTM".equals(user.getRole()) ||
                             user.getPermissions().stream().anyMatch(p -> "VIEW_ALL_TX".equals(p.getName()));
        boolean isOwner = tx.getUser().getEmail().equals(loggedInEmail);

        if (!hasAdminAccess && !isOwner) {
            throw new RuntimeException("Unauthorized: You can only view logs for your own transactions.");
        }

       return logRepo.findByTransactionIdOrderByTimestampDesc(id);   
    }

    public Transaction raiseIssue(Long id, String remarks, String loggedInEmail) {
        // 1. Find the transaction
        Transaction transaction = transactionRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        // 2. Strict State Machine Validation
        if (transaction.getStatus() != TransactionStatus.COMPLETED) {
            throw new RuntimeException("Issues can only be raised for completed transactions.");
        }

        // 3. Send it directly back to the Fund Manager's desk!
        transaction.setStatus(TransactionStatus.ISSUE_RAISED); 

        // 4. Save the transaction 
        Transaction savedTx = transactionRepo.save(transaction);

        // 5. Record the log
        recordLog(savedTx.getId(), loggedInEmail, "ISSUE_RAISED", "Payment Issue: " + remarks);

        return savedTx;
    }
}