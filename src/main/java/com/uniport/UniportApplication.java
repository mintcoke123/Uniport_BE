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
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

@SpringBootApplication
@EnableScheduling
public class UniportApplication {

	public static void main(String[] args) {
		var app = SpringApplication.run(UniportApplication.class, args);
		Environment env = app.getEnvironment();
		String active = String.join(",", env.getActiveProfiles().length > 0 ? env.getActiveProfiles() : env.getDefaultProfiles());
		String ddlAuto = env.getProperty("spring.jpa.hibernate.ddl-auto", "not-set");
		System.out.println("[uniport] spring.profiles.active=" + active + " spring.jpa.hibernate.ddl-auto=" + ddlAuto);
	}

	@Bean
	public CommandLineRunner startupRunner(
			UserRepository userRepository,
			CompetitionRepository competitionRepository,
			PasswordEncoder passwordEncoder,
			AuthService authService,
			StockService stockService,
			TradeService tradeService,
			@Value("${uniport.admin.student-id:22011739}") String adminStudentId,
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
				String testStudentId = "25000002";
				String testPassword = "password";
				User testUser = userRepository.findByStudentId(testStudentId).orElse(null);
				if (testUser == null) {
					testUser = User.builder()
							.studentId(testStudentId)
							.username(testStudentId)
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
					LoginRequestDTO loginRequest = new LoginRequestDTO(testStudentId, testPassword);
					authService.authenticateUser(loginRequest);
				} catch (Exception e) {
				}

				try {
					stockService.getStockPrice("005930");
				} catch (Exception e) {
				}

				try {
					User user = userRepository.findByStudentId(testStudentId).orElseThrow();
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

			if (adminStudentId != null && !adminStudentId.isBlank() && adminPassword != null && !adminPassword.isBlank()) {
				User adminUser = userRepository.findByStudentId(adminStudentId).orElse(null);
				if (adminUser == null) {
					org.slf4j.LoggerFactory.getLogger(UniportApplication.class).info("[uniport] Admin user created: studentId={}", adminStudentId);
					adminUser = User.builder()
							.studentId(adminStudentId)
							.username(adminStudentId)
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
					org.slf4j.LoggerFactory.getLogger(UniportApplication.class).info("[uniport] Admin user updated: studentId={}", adminStudentId);
				}
			} else {
				org.slf4j.LoggerFactory.getLogger(UniportApplication.class).warn("[uniport] Admin user skipped: studentId or password not set (studentId={}, passwordSet={})", adminStudentId, adminPassword != null && !adminPassword.isBlank());
			}

			// SISU-admin (준관리자): /SISU-admin 페이지만 접근. 학번 26999999, 비밀번호 SISUadmin123!!
			String sisuAdminStudentId = "26999999";
			String sisuAdminPassword = "SISUadmin123!!";
			User sisuAdminUser = userRepository.findByStudentId(sisuAdminStudentId).orElse(null);
			if (sisuAdminUser == null) {
				org.slf4j.LoggerFactory.getLogger(UniportApplication.class).info("[uniport] SISU-admin user created: studentId={}", sisuAdminStudentId);
				sisuAdminUser = User.builder()
						.studentId(sisuAdminStudentId)
						.username(sisuAdminStudentId)
						.password(passwordEncoder.encode(sisuAdminPassword))
						.nickname("SISU-admin")
						.totalAssets(BigDecimal.ZERO)
						.investmentAmount(BigDecimal.ZERO)
						.profitLoss(BigDecimal.ZERO)
						.profitLossRate(BigDecimal.ZERO)
						.teamId(null)
						.role("sisu_admin")
						.build();
				userRepository.save(sisuAdminUser);
			} else {
				sisuAdminUser.setPassword(passwordEncoder.encode(sisuAdminPassword));
				sisuAdminUser.setNickname("SISU-admin");
				if (!"sisu_admin".equals(sisuAdminUser.getRole())) sisuAdminUser.setRole("sisu_admin");
				userRepository.save(sisuAdminUser);
				org.slf4j.LoggerFactory.getLogger(UniportApplication.class).info("[uniport] SISU-admin user updated: studentId={}", sisuAdminStudentId);
			}
		};
	}
}
