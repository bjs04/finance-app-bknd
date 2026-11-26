package com.cag.finance_app_backend.Repositories;

import com.cag.finance_app_backend.Models.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // For Normal Users: Fetches only bills matching their logged-in email, newest first
    Page<Transaction> findByUserEmailOrderByIdDesc(String userEmail,Pageable pageable);

    // For Admins/Finance: sorted by newest first 
    Page<Transaction> findAllByOrderByIdDesc(Pageable pageable);

    //For admins
    @Query("SELECT t FROM Transaction t WHERE " +
           "LOWER(t.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(t.personName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "CAST(t.id AS string) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "ORDER BY t.id DESC")
    Page<Transaction> searchAllTransactions(@Param("searchTerm") String searchTerm, Pageable pageable);

    // 2. Search for Normal Users (Restricted by email)
    @Query("SELECT t FROM Transaction t WHERE t.user.email = :email AND (" +
           "LOWER(t.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(t.personName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "CAST(t.id AS string) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
           "ORDER BY t.id DESC")
    Page<Transaction> searchUserTransactions(@Param("email") String email, @Param("searchTerm") String searchTerm, Pageable pageable);
}