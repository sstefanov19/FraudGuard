package com.fraudguard.payments.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<TransactionEntity, String> {

    Optional<TransactionEntity> findByIdempotencyKey(String idempotencyKey);

    long countByDetailsAccountIdAndDetailsCreatedAtAfter(String accountId, Instant cutoff);

    @Query(value = """
            select avg(recent.amount)
            from (
                select amount
                from transactions
                where account_id = :accountId
                  and id <> :excludeId
                order by created_at desc
                limit :limit
            ) recent
            """, nativeQuery = true)
    BigDecimal trailingAverage(
            @Param("accountId") String accountId,
            @Param("excludeId") String excludeId,
            @Param("limit") int limit);

    @Query("""
            select count(t)
            from TransactionEntity t
            where t.details.accountId = :accountId
              and t.id <> :excludeId
            """)
    long priorCount(@Param("accountId") String accountId, @Param("excludeId") String excludeId);

    boolean existsByDetailsAccountIdAndDetailsDeviceIdAndIdNot(String accountId, String deviceId, String id);

    long countByDetailsAccountId(String accountId);
}
