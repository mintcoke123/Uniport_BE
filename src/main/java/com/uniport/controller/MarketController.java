package com.uniport.controller;

import com.uniport.dto.ApiResponse;
import com.uniport.dto.IndexChartPriceItemDTO;
import com.uniport.dto.MarketIndexDTO;
import com.uniport.dto.StockPriceDTO;
import com.uniport.service.MarketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/market")
public class MarketController {

    private final MarketService marketService;

    public MarketController(MarketService marketService) {
        this.marketService = marketService;
    }

    @GetMapping("/volume-rank")
    public ResponseEntity<ApiResponse<List<StockPriceDTO>>> getVolumeRank() {
        List<StockPriceDTO> list = marketService.getVolumeRank();
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @GetMapping("/fluctuation-rank")
    public ResponseEntity<ApiResponse<List<StockPriceDTO>>> getFluctuationRank() {
        List<StockPriceDTO> list = marketService.getFluctuationRank();
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @GetMapping("/falling-rank")
    public ResponseEntity<ApiResponse<List<StockPriceDTO>>> getFallingRank() {
        List<StockPriceDTO> list = marketService.getFallingRank();
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @GetMapping("/index")
    public ResponseEntity<ApiResponse<MarketIndexDTO>> getIndex(@RequestParam("code") String indexCode) {
        MarketIndexDTO dto = marketService.getMarketIndex(indexCode);
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    /** 일/주/월/년 지수 차트 시세. code=KOSPI|KOSDAQ, startDate/endDate=yyyyMMdd, period=D|W|M|Y */
    @GetMapping("/index-chart")
    public ResponseEntity<ApiResponse<List<IndexChartPriceItemDTO>>> getIndexChart(
            @RequestParam("code") String indexCode,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            @RequestParam(value = "period", defaultValue = "D") String period) {
        List<IndexChartPriceItemDTO> list = marketService.getIndexChartPrice(indexCode, startDate, endDate, period);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }
}
