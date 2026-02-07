package com.uniport.service;

import com.uniport.dto.OrderResponseDTO;
import com.uniport.dto.PlaceOrderRequestDTO;
import com.uniport.dto.TradeRequestDTO;
import com.uniport.dto.TradeResponseDTO;
import com.uniport.entity.Order;
import com.uniport.entity.OrderType;
import com.uniport.entity.OrderStatus;
import com.uniport.entity.TeamAccount;
import com.uniport.entity.TeamHolding;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.OrderRepository;
import com.uniport.repository.TeamAccountRepository;
import com.uniport.repository.TeamHoldingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class TradeService {

    private static final BigDecimal INITIAL_TEAM_BALANCE = new BigDecimal("10000000");

    private final OrderRepository orderRepository;
    private final KisApiService kisApiService;
    private final TeamAccountRepository teamAccountRepository;
    private final TeamHoldingRepository teamHoldingRepository;

    public TradeService(OrderRepository orderRepository, KisApiService kisApiService,
                        TeamAccountRepository teamAccountRepository,
                        TeamHoldingRepository teamHoldingRepository) {
        this.orderRepository = orderRepository;
        this.kisApiService = kisApiService;
        this.teamAccountRepository = teamAccountRepository;
        this.teamHoldingRepository = teamHoldingRepository;
    }

    /** User.teamId (예: "team-123")에서 팀 PK 추출 */
    private static Long parseTeamId(User user) {
        String tid = user.getTeamId();
        if (tid == null || tid.isBlank() || !tid.startsWith("team-")) {
            return null;
        }
        try {
            return Long.parseLong(tid.substring(5));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Transactional
    public OrderResponseDTO placeOrder(PlaceOrderRequestDTO request, User user) {
        if (request.getStockCode() == null || request.getStockCode().isBlank()) {
            throw new ApiException("Stock code is required", HttpStatus.BAD_REQUEST);
        }
        if (request.getQuantity() <= 0) {
            throw new ApiException("Quantity must be positive", HttpStatus.BAD_REQUEST);
        }
        if (request.getPrice() == null || request.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException("Price must be positive", HttpStatus.BAD_REQUEST);
        }
        if (request.getOrderType() == null) {
            throw new ApiException("Order type is required", HttpStatus.BAD_REQUEST);
        }

        Long teamId = parseTeamId(user);
        if (teamId == null) {
            throw new ApiException("팀에 소속된 후 거래할 수 있습니다.", HttpStatus.FORBIDDEN);
        }
        return executeTeamOrder(request, teamId, user);
    }

    /** 팀 지정 주문 (투표 통과 시 사용). teamId = 방(그룹) ID. */
    @Transactional
    public OrderResponseDTO placeOrderForTeam(PlaceOrderRequestDTO request, Long teamId, User orderUser) {
        if (request.getStockCode() == null || request.getStockCode().isBlank()) {
            throw new ApiException("Stock code is required", HttpStatus.BAD_REQUEST);
        }
        if (request.getQuantity() <= 0) {
            throw new ApiException("Quantity must be positive", HttpStatus.BAD_REQUEST);
        }
        if (request.getPrice() == null || request.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException("Price must be positive", HttpStatus.BAD_REQUEST);
        }
        if (request.getOrderType() == null) {
            throw new ApiException("Order type is required", HttpStatus.BAD_REQUEST);
        }
        if (teamId == null) {
            throw new ApiException("teamId is required", HttpStatus.BAD_REQUEST);
        }
        return executeTeamOrder(request, teamId, orderUser);
    }

    private OrderResponseDTO executeTeamOrder(PlaceOrderRequestDTO request, Long teamId, User orderUser) {
        try {
            String logLine = "{\"location\":\"TradeService:executeTeamOrder\",\"message\":\"order\",\"data\":{\"teamId\":" + teamId + ",\"stockCode\":\"" + (request.getStockCode() != null ? request.getStockCode().replace("\"", "\\\"") : "") + "\",\"quantity\":" + request.getQuantity() + "},\"timestamp\":" + System.currentTimeMillis() + "}\n";
            java.nio.file.Files.write(java.nio.file.Path.of("c:", "uniport", "uniport", ".cursor", "debug.log"), logLine.getBytes(java.nio.charset.StandardCharsets.UTF_8), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {}

        TeamAccount account = teamAccountRepository.findByTeamId(teamId)
                .orElseGet(() -> teamAccountRepository.save(TeamAccount.builder()
                        .teamId(teamId)
                        .cashBalance(INITIAL_TEAM_BALANCE)
                        .build()));

        BigDecimal amount = request.getPrice().multiply(BigDecimal.valueOf(request.getQuantity()));

        if (request.getOrderType() == OrderType.BUY) {
            if (account.getCashBalance().compareTo(amount) < 0) {
                throw new ApiException("팀 잔액이 부족합니다.", HttpStatus.BAD_REQUEST);
            }
            account.setCashBalance(account.getCashBalance().subtract(amount));
            teamAccountRepository.save(account);

            Optional<TeamHolding> opt = teamHoldingRepository.findByTeamIdAndStockCode(teamId, request.getStockCode());
            TeamHolding holding;
            if (opt.isPresent()) {
                holding = opt.get();
                int newQty = holding.getQuantity() + request.getQuantity();
                BigDecimal totalCost = holding.getAveragePurchasePrice().multiply(BigDecimal.valueOf(holding.getQuantity()))
                        .add(request.getPrice().multiply(BigDecimal.valueOf(request.getQuantity())));
                holding.setQuantity(newQty);
                holding.setAveragePurchasePrice(totalCost.divide(BigDecimal.valueOf(newQty), 4, RoundingMode.HALF_UP));
            } else {
                holding = TeamHolding.builder()
                        .teamId(teamId)
                        .stockCode(request.getStockCode())
                        .stockName(request.getStockName() != null && !request.getStockName().isBlank() ? request.getStockName() : null)
                        .quantity(request.getQuantity())
                        .averagePurchasePrice(request.getPrice())
                        .build();
            }
            if (request.getStockName() != null && !request.getStockName().isBlank()) {
                holding.setStockName(request.getStockName());
            }
            teamHoldingRepository.save(holding);
        } else {
            TeamHolding holding = teamHoldingRepository.findByTeamIdAndStockCode(teamId, request.getStockCode())
                    .orElseThrow(() -> new ApiException("보유 수량이 없어 매도할 수 없습니다.", HttpStatus.BAD_REQUEST));
            if (holding.getQuantity() < request.getQuantity()) {
                throw new ApiException("보유 수량이 부족합니다. 보유: " + holding.getQuantity(), HttpStatus.BAD_REQUEST);
            }
            account.setCashBalance(account.getCashBalance().add(amount));
            teamAccountRepository.save(account);

            int remain = holding.getQuantity() - request.getQuantity();
            if (remain <= 0) {
                teamHoldingRepository.delete(holding);
            } else {
                holding.setQuantity(remain);
                teamHoldingRepository.save(holding);
            }
        }

        OrderResponseDTO kisResponse;
        try {
            kisResponse = kisApiService.placeOrder(
                    request.getStockCode(),
                    request.getQuantity(),
                    request.getPrice(),
                    request.getOrderType());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("Order failed: " + e.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
        }

        Order order = Order.builder()
                .user(orderUser)
                .teamId(teamId)
                .stockCode(request.getStockCode())
                .quantity(request.getQuantity())
                .price(request.getPrice())
                .orderType(request.getOrderType())
                .status(OrderStatus.COMPLETED)
                .orderDate(LocalDateTime.now())
                .build();
        order = orderRepository.save(order);

        return OrderResponseDTO.builder()
                .orderId(order.getId())
                .stockCode(order.getStockCode())
                .quantity(order.getQuantity())
                .price(order.getPrice())
                .orderType(order.getOrderType())
                .status(order.getStatus())
                .orderDate(order.getOrderDate())
                .externalOrderNo(kisResponse.getExternalOrderNo())
                .message(kisResponse.getMessage())
                .build();
    }

    /** 명세 §3-6: stockId → 종목코드(6자리), side → buy/sell, 응답 success, orderId, executedAt */
    @Transactional
    public TradeResponseDTO placeOrderFromSpec(TradeRequestDTO request, User user) {
        if (request.getStockId() == null) {
            throw new ApiException("stockId is required", HttpStatus.BAD_REQUEST);
        }
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new ApiException("quantity must be positive", HttpStatus.BAD_REQUEST);
        }
        if (request.getPricePerShare() == null || request.getPricePerShare().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException("pricePerShare must be positive", HttpStatus.BAD_REQUEST);
        }
        String stockCode = String.format("%06d", request.getStockId());
        OrderType type = "sell".equalsIgnoreCase(request.getSide()) ? OrderType.SELL : OrderType.BUY;

        PlaceOrderRequestDTO place = PlaceOrderRequestDTO.builder()
                .stockCode(stockCode)
                .quantity(request.getQuantity())
                .price(request.getPricePerShare())
                .orderType(type)
                .build();
        OrderResponseDTO result = placeOrder(place, user);

        String executedAt = result.getOrderDate() != null
                ? result.getOrderDate().format(DateTimeFormatter.ISO_DATE_TIME)
                : LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);

        return TradeResponseDTO.builder()
                .success(true)
                .message("Order executed.")
                .orderId(result.getExternalOrderNo() != null ? result.getExternalOrderNo() : String.valueOf(result.getOrderId()))
                .executedAt(executedAt)
                .build();
    }

    public List<OrderResponseDTO> getOrders(User user) {
        List<Order> orders = orderRepository.findByUser_IdOrderByOrderDateDesc(user.getId());
        return orders.stream().map(this::toOrderResponseDTO).collect(Collectors.toList());
    }

    @Transactional
    public OrderResponseDTO cancelOrder(Long orderId, User user) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException("Order not found", HttpStatus.NOT_FOUND));
        if (!order.getUser().getId().equals(user.getId())) {
            throw new ApiException("Not authorized to cancel this order", HttpStatus.FORBIDDEN);
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ApiException("Order already cancelled", HttpStatus.BAD_REQUEST);
        }

        try {
            kisApiService.cancelOrder("", String.valueOf(order.getId()));
        } catch (Exception e) {
            throw new ApiException("Cancel failed: " + e.getMessage(), HttpStatus.SERVICE_UNAVAILABLE);
        }

        order.setStatus(OrderStatus.CANCELLED);
        order = orderRepository.save(order);
        return toOrderResponseDTO(order);
    }

    private OrderResponseDTO toOrderResponseDTO(Order order) {
        return OrderResponseDTO.builder()
                .orderId(order.getId())
                .stockCode(order.getStockCode())
                .quantity(order.getQuantity())
                .price(order.getPrice())
                .orderType(order.getOrderType())
                .status(order.getStatus())
                .orderDate(order.getOrderDate())
                .build();
    }
}
