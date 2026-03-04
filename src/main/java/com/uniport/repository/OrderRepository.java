package com.uniport.repository;

import com.uniport.entity.Order;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUser_Id(Long userId);

    void deleteByUser_Id(Long userId);

    List<Order> findByUser_IdOrderByOrderDateDesc(Long userId);

    List<Order> findByTeamIdAndStockCodeOrderByOrderDateDesc(Long teamId, String stockCode);

    /** 팀(방)별 주문 목록. 관리자 거래내역 로그에서 바로 체결(placeTrade) 건 포함용 */
    List<Order> findByTeamIdOrderByOrderDateDesc(Long teamId);
}
