package com.uniport.service.feedback;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class GroupInvestmentFeedbackCalculator {

    private static final int MONEY_SCALE = 0;
    private static final int RATE_SCALE = 1;

    public GroupInvestmentFeedbackCalculation calculate(GroupInvestmentSessionSnapshot session,
                                                        List<ExecutedTradeSnapshot> trades,
                                                        Map<String, BigDecimal> endPrices) {
        if (session == null || session.initialCapital() == null || session.initialCapital().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("initialCapital must be positive");
        }

        List<ExecutedTradeSnapshot> orderedTrades = trades == null ? List.of() : trades.stream()
                .sorted(Comparator.comparing(ExecutedTradeSnapshot::executedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        Map<String, BigDecimal> prices = endPrices != null ? endPrices : Map.of();

        BigDecimal cash = session.initialCapital();
        Map<String, Integer> holdingQuantities = new HashMap<>();
        Map<String, ArrayDeque<OpenLot>> openLotsByStockCode = new HashMap<>();
        Map<Long, BigDecimal> pnlByTradeId = new HashMap<>();
        for (ExecutedTradeSnapshot trade : orderedTrades) {
            validateTrade(trade);
            BigDecimal fee = zeroIfNull(trade.feeAmount());
            BigDecimal amount = trade.executedPrice().multiply(BigDecimal.valueOf(trade.quantity()));
            pnlByTradeId.putIfAbsent(trade.tradeId(), BigDecimal.ZERO);

            if (trade.side() == TradeSide.BUY) {
                cash = cash.subtract(amount).subtract(fee);
                holdingQuantities.merge(trade.stockCode(), trade.quantity(), Integer::sum);
                openLotsByStockCode
                        .computeIfAbsent(trade.stockCode(), ignored -> new ArrayDeque<>())
                        .addLast(new OpenLot(trade, trade.quantity()));
            } else {
                cash = cash.add(amount).subtract(fee);
                int nextQuantity = holdingQuantities.getOrDefault(trade.stockCode(), 0) - trade.quantity();
                if (nextQuantity < 0) {
                    throw new IllegalArgumentException("holding quantity cannot be negative for " + trade.stockCode());
                }
                holdingQuantities.put(trade.stockCode(), nextQuantity);
                BigDecimal realized = closeLots(openLotsByStockCode.get(trade.stockCode()), trade);
                pnlByTradeId.merge(trade.tradeId(), realized, BigDecimal::add);
            }
        }

        BigDecimal holdingValue = BigDecimal.ZERO;
        for (Map.Entry<String, Integer> entry : holdingQuantities.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }
            BigDecimal endPrice = prices.get(entry.getKey());
            if (endPrice == null || endPrice.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("missing end price for " + entry.getKey());
            }
            holdingValue = holdingValue.add(endPrice.multiply(BigDecimal.valueOf(entry.getValue())));
        }
        for (ArrayDeque<OpenLot> lots : openLotsByStockCode.values()) {
            for (OpenLot lot : lots) {
                if (lot.remainingQuantity <= 0) {
                    continue;
                }
                BigDecimal endPrice = prices.get(lot.trade.stockCode());
                if (endPrice == null || endPrice.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("missing end price for " + lot.trade.stockCode());
                }
                BigDecimal feeShare = feeShare(lot.trade.feeAmount(), lot.remainingQuantity, lot.trade.quantity());
                BigDecimal unrealized = endPrice.subtract(lot.trade.executedPrice())
                        .multiply(BigDecimal.valueOf(lot.remainingQuantity))
                        .subtract(feeShare);
                pnlByTradeId.merge(lot.trade.tradeId(), unrealized, BigDecimal::add);
            }
        }

        List<TradePnlSnapshot> tradePnls = new ArrayList<>();
        for (ExecutedTradeSnapshot trade : orderedTrades) {
            BigDecimal pnlAmount = money(pnlByTradeId.getOrDefault(trade.tradeId(), BigDecimal.ZERO));
            tradePnls.add(new TradePnlSnapshot(
                    trade.tradeId(),
                    trade.decisionId(),
                    trade.proposerId(),
                    trade.stockCode(),
                    trade.stockName(),
                    trade.side(),
                    trade.quantity(),
                    trade.executedPrice(),
                    trade.reason(),
                    pnlAmount,
                    pnlRate(pnlAmount, trade.executedPrice().multiply(BigDecimal.valueOf(trade.quantity()))),
                    trade.executedAt()
            ));
        }

        BigDecimal finalEquity = money(cash.add(holdingValue));
        BigDecimal profitAmount = money(finalEquity.subtract(session.initialCapital()));
        BigDecimal returnRate = pnlRate(profitAmount, session.initialCapital());

        return new GroupInvestmentFeedbackCalculation(
                session.sessionId(),
                session.roomId(),
                money(session.initialCapital()),
                money(cash),
                money(holdingValue),
                finalEquity,
                profitAmount,
                returnRate,
                List.copyOf(tradePnls),
                selectBest(tradePnls),
                selectWorst(tradePnls)
        );
    }

    private BigDecimal closeLots(ArrayDeque<OpenLot> lots, ExecutedTradeSnapshot sellTrade) {
        if (lots == null || lots.isEmpty()) {
            throw new IllegalArgumentException("missing buy lots for " + sellTrade.stockCode());
        }
        int remainingToClose = sellTrade.quantity();
        BigDecimal realized = BigDecimal.ZERO;
        while (remainingToClose > 0) {
            OpenLot lot = lots.peekFirst();
            if (lot == null) {
                throw new IllegalArgumentException("missing buy lots for " + sellTrade.stockCode());
            }
            int closedQuantity = Math.min(remainingToClose, lot.remainingQuantity);
            BigDecimal buyFeeShare = feeShare(lot.trade.feeAmount(), closedQuantity, lot.trade.quantity());
            BigDecimal sellFeeShare = feeShare(sellTrade.feeAmount(), closedQuantity, sellTrade.quantity());
            BigDecimal pnl = sellTrade.executedPrice().subtract(lot.trade.executedPrice())
                    .multiply(BigDecimal.valueOf(closedQuantity))
                    .subtract(buyFeeShare)
                    .subtract(sellFeeShare);
            realized = realized.add(pnl);

            lot.remainingQuantity -= closedQuantity;
            remainingToClose -= closedQuantity;
            if (lot.remainingQuantity <= 0) {
                lots.removeFirst();
            }
        }
        return realized;
    }

    private Optional<TradePnlSnapshot> selectBest(List<TradePnlSnapshot> tradePnls) {
        if (tradePnls == null || tradePnls.isEmpty()) {
            return Optional.empty();
        }
        if (tradePnls.size() == 1) {
            TradePnlSnapshot only = tradePnls.get(0);
            return only.pnlAmount().compareTo(BigDecimal.ZERO) >= 0 ? Optional.of(only) : Optional.empty();
        }
        return tradePnls.stream().max(bestComparator());
    }

    private Optional<TradePnlSnapshot> selectWorst(List<TradePnlSnapshot> tradePnls) {
        if (tradePnls == null || tradePnls.isEmpty()) {
            return Optional.empty();
        }
        if (tradePnls.size() == 1) {
            TradePnlSnapshot only = tradePnls.get(0);
            return only.pnlAmount().compareTo(BigDecimal.ZERO) < 0 ? Optional.of(only) : Optional.empty();
        }
        return tradePnls.stream().min(Comparator
                .comparing(TradePnlSnapshot::pnlAmount)
                .thenComparing(item -> item.pnlAmount().abs())
                .thenComparing(item -> item.pnlRate().abs())
                .thenComparing(TradePnlSnapshot::executedAt, Comparator.nullsLast(Comparator.naturalOrder())));
    }

    private Comparator<TradePnlSnapshot> bestComparator() {
        return Comparator
                .comparing(TradePnlSnapshot::pnlAmount)
                .thenComparing(item -> item.pnlAmount().abs())
                .thenComparing(item -> item.pnlRate().abs())
                .thenComparing(TradePnlSnapshot::executedAt, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private void validateTrade(ExecutedTradeSnapshot trade) {
        if (trade == null) {
            throw new IllegalArgumentException("trade is required");
        }
        if (trade.tradeId() == null) {
            throw new IllegalArgumentException("tradeId is required");
        }
        if (trade.stockCode() == null || trade.stockCode().isBlank()) {
            throw new IllegalArgumentException("stockCode is required");
        }
        if (trade.side() == null) {
            throw new IllegalArgumentException("side is required");
        }
        if (trade.quantity() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (trade.executedPrice() == null || trade.executedPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("executedPrice must be positive");
        }
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static BigDecimal feeShare(BigDecimal fee, int quantity, int totalQuantity) {
        if (fee == null || totalQuantity <= 0 || quantity <= 0) {
            return BigDecimal.ZERO;
        }
        return fee.multiply(BigDecimal.valueOf(quantity))
                .divide(BigDecimal.valueOf(totalQuantity), 8, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal pnlRate(BigDecimal pnlAmount, BigDecimal baseAmount) {
        if (baseAmount == null || baseAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(RATE_SCALE, RoundingMode.HALF_UP);
        }
        return pnlAmount.divide(baseAmount, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    private static final class OpenLot {
        private final ExecutedTradeSnapshot trade;
        private int remainingQuantity;

        private OpenLot(ExecutedTradeSnapshot trade, int remainingQuantity) {
            this.trade = trade;
            this.remainingQuantity = remainingQuantity;
        }
    }
}
