package com.uniport.service;

import com.uniport.entity.PointTransaction;
import com.uniport.entity.PointWallet;
import com.uniport.entity.User;
import com.uniport.repository.PointTransactionRepository;
import com.uniport.repository.PointWalletRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PointLedgerServiceTest {

    @Test
    void earnCreatesWalletAndTransactionWhenSourceWasNotUsed() {
        PointWalletRepository walletRepository = mock(PointWalletRepository.class);
        PointTransactionRepository transactionRepository = mock(PointTransactionRepository.class);
        PointLedgerService service = new PointLedgerService(walletRepository, transactionRepository);
        User user = User.builder().id(10L).nickname("A").studentId("1").password("p").build();

        when(transactionRepository.findBySourceTypeAndSourceId("GROUP_FEEDBACK_REPORT", "member-1"))
                .thenReturn(Optional.empty());
        when(walletRepository.findByUser_IdForUpdate(10L)).thenReturn(Optional.empty());
        when(walletRepository.save(any(PointWallet.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(PointTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PointTransaction transaction = service.earn(user, 800, "GROUP_FEEDBACK_REPORT", "member-1", "피드백 리포트 기여도 보상");

        assertEquals("EARN", transaction.getType());
        assertEquals(800, transaction.getAmount());
        assertEquals(800, transaction.getBalanceAfter());
        verify(walletRepository).save(any(PointWallet.class));
        verify(transactionRepository).save(any(PointTransaction.class));
    }

    @Test
    void earnIsIdempotentForSameSource() {
        PointWalletRepository walletRepository = mock(PointWalletRepository.class);
        PointTransactionRepository transactionRepository = mock(PointTransactionRepository.class);
        PointLedgerService service = new PointLedgerService(walletRepository, transactionRepository);
        User user = User.builder().id(10L).nickname("A").studentId("1").password("p").build();
        PointTransaction existing = PointTransaction.builder()
                .user(user)
                .type("EARN")
                .amount(800)
                .balanceAfter(1200)
                .sourceType("GROUP_FEEDBACK_REPORT")
                .sourceId("member-1")
                .build();

        when(transactionRepository.findBySourceTypeAndSourceId("GROUP_FEEDBACK_REPORT", "member-1"))
                .thenReturn(Optional.of(existing));

        PointTransaction transaction = service.earn(user, 800, "GROUP_FEEDBACK_REPORT", "member-1", "피드백 리포트 기여도 보상");

        assertEquals(existing, transaction);
        verify(walletRepository, never()).save(any(PointWallet.class));
        verify(transactionRepository, never()).save(any(PointTransaction.class));
    }

    @Test
    void deductCreatesUseTransactionAndAllowsNegativeBalance() {
        PointWalletRepository walletRepository = mock(PointWalletRepository.class);
        PointTransactionRepository transactionRepository = mock(PointTransactionRepository.class);
        PointLedgerService service = new PointLedgerService(walletRepository, transactionRepository);
        User user = User.builder().id(10L).nickname("A").studentId("1").password("p").build();

        when(transactionRepository.findBySourceTypeAndSourceId("GROUP_FEEDBACK_REPORT", "member-2"))
                .thenReturn(Optional.empty());
        when(walletRepository.findByUser_IdForUpdate(10L)).thenReturn(Optional.empty());
        when(walletRepository.save(any(PointWallet.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(PointTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PointTransaction transaction = service.deduct(user, 300, "GROUP_FEEDBACK_REPORT", "member-2", "피드백 리포트 기여도 차감");

        assertEquals("USE", transaction.getType());
        assertEquals(-300, transaction.getAmount());
        assertEquals(-300, transaction.getBalanceAfter());
        verify(walletRepository).save(any(PointWallet.class));
        verify(transactionRepository).save(any(PointTransaction.class));
    }
}
