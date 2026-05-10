package com.uniport.service;

import com.uniport.entity.PointTransaction;
import com.uniport.entity.PointWallet;
import com.uniport.entity.User;
import com.uniport.repository.PointTransactionRepository;
import com.uniport.repository.PointWalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PointLedgerService {

    private final PointWalletRepository walletRepository;
    private final PointTransactionRepository transactionRepository;

    public PointLedgerService(PointWalletRepository walletRepository,
                              PointTransactionRepository transactionRepository) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public PointTransaction earn(User user,
                                 int amount,
                                 String sourceType,
                                 String sourceId,
                                 String description) {
        validatePositiveAmount(amount);
        return apply(user, amount, "EARN", sourceType, sourceId, description);
    }

    @Transactional
    public PointTransaction deduct(User user,
                                   int amount,
                                   String sourceType,
                                   String sourceId,
                                   String description) {
        validatePositiveAmount(amount);
        return apply(user, -amount, "USE", sourceType, sourceId, description);
    }

    private PointTransaction apply(User user,
                                   int signedAmount,
                                   String type,
                                   String sourceType,
                                   String sourceId,
                                   String description) {
        validate(user, signedAmount, sourceType, sourceId);
        return transactionRepository.findBySourceTypeAndSourceId(sourceType, sourceId)
                .orElseGet(() -> createTransaction(user, signedAmount, type, sourceType, sourceId, description));
    }

    private PointTransaction createTransaction(User user,
                                               int signedAmount,
                                               String type,
                                               String sourceType,
                                               String sourceId,
                                               String description) {
        PointWallet wallet = walletRepository.findByUser_IdForUpdate(user.getId())
                .orElseGet(() -> PointWallet.builder()
                        .user(user)
                        .balance(0)
                        .build());
        int nextBalance = safeBalance(wallet) + signedAmount;
        wallet.setBalance(nextBalance);
        walletRepository.save(wallet);

        return transactionRepository.save(PointTransaction.builder()
                .user(user)
                .type(type)
                .amount(signedAmount)
                .balanceAfter(nextBalance)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .description(description)
                .build());
    }

    private void validate(User user, int signedAmount, String sourceType, String sourceId) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("user is required");
        }
        if (signedAmount == 0) {
            throw new IllegalArgumentException("amount must not be zero");
        }
        if (sourceType == null || sourceType.isBlank()) {
            throw new IllegalArgumentException("sourceType is required");
        }
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId is required");
        }
    }

    private void validatePositiveAmount(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }

    private int safeBalance(PointWallet wallet) {
        return wallet.getBalance() != null ? wallet.getBalance() : 0;
    }
}
