package com.uniport.repository;

import com.uniport.entity.Order;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUser_Id(Long userId);

    List<Order> findByUser_IdOrderByOrderDateDesc(Long userId);

    List<Order> findByTeamIdAndStockCodeOrderByOrderDateDesc(Long teamId, String stockCode);
}
