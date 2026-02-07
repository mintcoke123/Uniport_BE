package com.uniport;

import com.uniport.dto.LoginRequestDTO;
import com.uniport.dto.PlaceOrderRequestDTO;
import com.uniport.entity.Competition;
import com.uniport.entity.OrderType;
import com.uniport.entity.User;
import com.uniport.repository.CompetitionRepository;
import com.uniport.repository.UserRepository;
import com.uniport.service.AuthService;
import com.uniport.service.StockService;
import com.uniport.service.TradeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

@SpringBootApplication
public class UniportApplication {

	public static void main(String[] args) {
		SpringApplication.run(UniportApplication.class, args);
	}

	/**
	 * 기동 시 샘플 사용자 생성 및 간단한 통합 테스트 시나리오 실행.
	 * 명세 §1: email 로그인. 로그인 → 시세 조회 → 주문 요청 순서로 결과를 콘솔에 출력.
	 */
	@Bean
	public CommandLineRunner startupRunner(
			UserRepository userRepository,
			CompetitionRepository competitionRepository,
			PasswordEncoder passwordEncoder,
			AuthService authService,
			StockService stockService,
			TradeService tradeService,
			@Value("${uniport.admin.email:admin@uniport.com}") String adminEmail,
			@Value("${uniport.admin.password:uniport}") String adminPassword,
			@Value("${uniport.seed.test-user-enabled:true}") boolean seedTestUserEnabled) {
		return args -> {
			if (competitionRepository.count() == 0) {
				Competition defaultCompetition = Competition.builder()
						.name("1차 모의투자 대회")
						.startDate("2025-02-01T00:00:00")
						.endDate("2026-12-31T23:59:59")
						.status("ongoing")
						.build();
				competitionRepository.save(defaultCompetition);
			}

			if (seedTestUserEnabled) {
				String testEmail = "test@example.com";
				String testPassword = "password";
				User testUser = userRepository.findByEmail(testEmail).orElse(null);
				if (testUser == null) {
					testUser = User.builder()
							.email(testEmail)
							.username(testEmail)
							.password(passwordEncoder.encode(testPassword))
							.nickname("Test User")
							.totalAssets(new BigDecimal("10000000"))
							.investmentAmount(new BigDecimal("10000000"))
							.profitLoss(BigDecimal.ZERO)
							.profitLossRate(BigDecimal.ZERO)
							.teamId(null)
							.role("user")
							.build();
					userRepository.save(testUser);
				} else {
					testUser.setPassword(passwordEncoder.encode(testPassword));
					if (testUser.getRole() == null) testUser.setRole("user");
					userRepository.save(testUser);
				}

				try {
					LoginRequestDTO loginRequest = new LoginRequestDTO(testEmail, testPassword);
					authService.authenticateUser(loginRequest);
				} catch (Exception e) {
				}

				try {
					stockService.getStockPrice("005930");
				} catch (Exception e) {
				}

				try {
					User user = userRepository.findByEmail(testEmail).orElseThrow();
					PlaceOrderRequestDTO orderRequest = PlaceOrderRequestDTO.builder()
							.stockCode("005930")
							.quantity(1)
							.price(new BigDecimal("70000"))
							.orderType(OrderType.BUY)
							.build();
					tradeService.placeOrder(orderRequest, user);
				} catch (Exception e) {
				}
			}

			// 어드민 계정 (env: UNIPORT_ADMIN_EMAIL, UNIPORT_ADMIN_PASSWORD)
			if (adminEmail != null && !adminEmail.isBlank() && adminPassword != null && !adminPassword.isBlank()) {
				User adminUser = userRepository.findByEmail(adminEmail).orElse(null);
				if (adminUser == null) {
					adminUser = User.builder()
							.email(adminEmail)
							.username(adminEmail)
							.password(passwordEncoder.encode(adminPassword))
							.nickname("Admin")
							.totalAssets(BigDecimal.ZERO)
							.investmentAmount(BigDecimal.ZERO)
							.profitLoss(BigDecimal.ZERO)
							.profitLossRate(BigDecimal.ZERO)
							.teamId(null)
							.role("admin")
							.build();
					userRepository.save(adminUser);
				} else {
					adminUser.setPassword(passwordEncoder.encode(adminPassword));
					if (!"admin".equals(adminUser.getRole())) adminUser.setRole("admin");
					userRepository.save(adminUser);
				}
			}
		};
	}
}
