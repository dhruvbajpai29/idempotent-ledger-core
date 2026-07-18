package com.ledger.core.service;

import com.ledger.core.entity.Wallet;
import com.ledger.core.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final WalletRepository walletRepository;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public String processTransfer(String idempotencyKey, Long senderId, Long receiverId, BigDecimal amount) {
        
        // 1. Idempotency Check via Redis (Prevents double processing)
        Boolean isNewRequest = redisTemplate.opsForValue()
                .setIfAbsent("idem_key:" + idempotencyKey, "PROCESSING", Duration.ofHours(24));
                
        if (Boolean.FALSE.equals(isNewRequest)) {
            return "Transaction already processed or in progress for this Idempotency Key.";
        }

        try {
            // 2. Fetch with Pessimistic Lock to prevent race conditions
            Wallet sender = walletRepository.findByIdForUpdate(senderId)
                    .orElseThrow(() -> new RuntimeException("Sender wallet not found"));
            Wallet receiver = walletRepository.findByIdForUpdate(receiverId)
                    .orElseThrow(() -> new RuntimeException("Receiver wallet not found"));

            // 3. Business Logic Validation
            if (sender.getBalance().compareTo(amount) < 0) {
                throw new RuntimeException("Insufficient funds");
            }

            // 4. Mutate State
            sender.setBalance(sender.getBalance().subtract(amount));
            receiver.setBalance(receiver.getBalance().add(amount));

            walletRepository.save(sender);
            walletRepository.save(receiver);

            // 5. Update Cache with Success
            redisTemplate.opsForValue().set("idem_key:" + idempotencyKey, "SUCCESS", Duration.ofHours(24));
            
            return "Transaction Successful";
            
        } catch (Exception e) {
            // Clear idempotency key on failure so client can retry
            redisTemplate.delete("idem_key:" + idempotencyKey);
            throw e;
        }
    }
}
