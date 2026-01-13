package com.tanvan.ecommerce.controller;

import com.tanvan.ecommerce.entity.Airline;
import com.tanvan.ecommerce.services.AirlineService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/flights")
@CrossOrigin(origins = "*")
public class AirlineController {

    private final AirlineService airlineService;

    public AirlineController(AirlineService airlineService) {
        this.airlineService = airlineService;
    }

    @GetMapping
    public ResponseEntity<Map<String, List<Airline>>> getFlights(@RequestParam String iata) {
        return ResponseEntity.ok(airlineService.fetchAndSaveAllFlights(iata));
    }

    @GetMapping("/cached")
    public ResponseEntity<Map<String, List<Airline>>> getCachedFlights(@RequestParam String iata) {
        return ResponseEntity.ok(airlineService.getFlightsFromDatabase(iata));
    }

    @GetMapping("/departures")
    public ResponseEntity<List<Airline>> getDepartures(@RequestParam String iata) {
        return ResponseEntity.ok(airlineService.fetchAndSaveDepartures(iata));
    }

    @GetMapping("/arrivals")
    public ResponseEntity<List<Airline>> getArrivals(@RequestParam String iata) {
        return ResponseEntity.ok(airlineService.fetchAndSaveArrivals(iata));
    }

    /**
     * ✅ CLEAR CACHE cho một airport
     * DELETE /api/flights/cache?iata=SFO
     */
    @DeleteMapping("/cache")
    public ResponseEntity<String> clearCache(@RequestParam String iata) {
        airlineService.clearCache(iata);
        return ResponseEntity.ok("Cache cleared for: " + iata);
    }

    /**
     * ✅ CLEAR TẤT CẢ CACHE
     * DELETE /api/flights/cache/all
     */
    @DeleteMapping("/cache/all")
    public ResponseEntity<String> clearAllCache() {
        airlineService.clearAllCache();
        return ResponseEntity.ok("All cache cleared successfully");
    }

    /**
     * ✅ FORCE REFRESH - Xóa cache và fetch lại
     * POST /api/flights/refresh?iata=SFO
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, List<Airline>>> refreshFlights(@RequestParam String iata) {
        airlineService.clearCache(iata);
        return ResponseEntity.ok(airlineService.fetchAndSaveAllFlights(iata));
    }
}