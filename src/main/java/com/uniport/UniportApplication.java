package com.uniport;

import com.uniport.dto.LoginRequestDTO;
import com.uniport.dto.PlaceOrderRequestDTO;
import com.uniport.entity.Competition;
import com.uniport.entity.GifticonInventory;
import com.uniport.entity.OrderType;
import com.uniport.entity.PointShopProduct;
import com.uniport.entity.User;
import com.uniport.repository.CompetitionRepository;
import com.uniport.repository.GifticonInventoryRepository;
import com.uniport.repository.PointShopProductRepository;
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
import java.time.LocalDateTime;
import java.util.List;

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
			PointShopProductRepository pointShopProductRepository,
			GifticonInventoryRepository gifticonInventoryRepository,
			PasswordEncoder passwordEncoder,
			AuthService authService,
			StockService stockService,
			TradeService tradeService,
			@Value("${uniport.admin.student-id:22011739}") String adminStudentId,
			@Value("${uniport.admin.password:uniport}") String adminPassword,
			@Value("${uniport.sisu-admin.student-id:26999999}") String sisuAdminStudentId,
			@Value("${uniport.sisu-admin.password:SISUadmin123!!}") String sisuAdminPassword,
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

			if (pointShopProductRepository.count() == 0) {
				List<PointShopProduct> products = List.of(
						PointShopProduct.builder()
								.brand("스타벅스")
								.name("아이스 카페 아메리카노 T")
								.category("CAFE")
								.pricePoint(4500)
								.imageUrl("https://image.istarbucks.co.kr/upload/store/skuimg/2021/04/[9200000004786]_20210430112319040.jpg")
								.description("스타벅스 매장에서 사용할 수 있는 아이스 카페 아메리카노 Tall 교환권입니다.")
								.notice("교환 신청 후 마이페이지 교환 내역에서 기프티콘 코드와 유효기간을 확인할 수 있습니다.")
								.status("ACTIVE")
								.stockCount(3)
								.sortOrder(1)
								.build(),
						PointShopProduct.builder()
								.brand("CU")
								.name("모바일 금액권 1,000원")
								.category("CONVENIENCE")
								.pricePoint(1500)
								.imageUrl("https://static.bgfretail.com/images/brand/brand_cu.png")
								.description("전국 CU 편의점에서 사용할 수 있는 모바일 금액권입니다.")
								.notice("일부 특수 매장에서는 사용이 제한될 수 있습니다. 잔액 환불은 제공되지 않습니다.")
								.status("ACTIVE")
								.stockCount(3)
								.sortOrder(2)
								.build(),
						PointShopProduct.builder()
								.brand("GS25")
								.name("모바일 금액권 5,000원")
								.category("CONVENIENCE")
								.pricePoint(5500)
								.imageUrl("https://www.gsretail.com/_ui/desktop/common/images/gscvs/logo.png")
								.description("전국 GS25 편의점에서 사용할 수 있는 모바일 금액권입니다.")
								.notice("담배, 주류 등 일부 품목과 특수 매장에서는 사용이 제한될 수 있습니다.")
								.status("ACTIVE")
								.stockCount(3)
								.sortOrder(3)
								.build(),
						PointShopProduct.builder()
								.brand("BHC")
								.name("후라이드 치킨 + 콜라 1.25L")
								.category("FOOD")
								.pricePoint(18000)
								.imageUrl("https://www.bhc.co.kr/images/common/logo.png")
								.description("BHC 매장에서 사용할 수 있는 후라이드 치킨 세트 교환권입니다.")
								.notice("매장 상황에 따라 일부 지점 사용이 제한될 수 있으며 배달비는 별도입니다.")
								.status("ACTIVE")
								.stockCount(3)
								.sortOrder(4)
								.build()
				);
				pointShopProductRepository.saveAll(products).forEach(product -> seedGifticonInventory(gifticonInventoryRepository, product));
				org.slf4j.LoggerFactory.getLogger(UniportApplication.class).info("[uniport] Point shop seed products created: count={}", products.size());
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

			// SISU-admin (준관리자): /SISU-admin 페이지만 접근. .env UNIPORT_SISU_ADMIN_STUDENT_ID, UNIPORT_SISU_ADMIN_PASSWORD
			if (sisuAdminStudentId != null && !sisuAdminStudentId.isBlank() && sisuAdminPassword != null && !sisuAdminPassword.isBlank()) {
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
			} else {
				org.slf4j.LoggerFactory.getLogger(UniportApplication.class).warn("[uniport] SISU-admin user skipped: studentId or password not set (studentId={}, passwordSet={})", sisuAdminStudentId, sisuAdminPassword != null && !sisuAdminPassword.isBlank());
			}
		};
	}

	private static void seedGifticonInventory(GifticonInventoryRepository gifticonInventoryRepository, PointShopProduct product) {
		LocalDateTime expiredAt = LocalDateTime.now().plusMonths(3);
		for (int index = 1; index <= 3; index++) {
			gifticonInventoryRepository.save(
					GifticonInventory.builder()
							.product(product)
							.gifticonCode("UNI-DEMO-" + product.getId() + "-" + index)
							.gifticonUrl("https://uniport.example/gifticons/" + product.getId() + "/" + index)
							.expiredAt(expiredAt)
							.status("AVAILABLE")
							.build()
			);
		}
	}
}
