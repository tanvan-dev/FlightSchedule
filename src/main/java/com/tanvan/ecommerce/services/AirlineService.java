package com.tanvan.ecommerce.services;

import com.tanvan.ecommerce.entity.Airline;
import com.tanvan.ecommerce.repository.AirlineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AirlineService {

    private final AirlineRepository airlineRepository;
    private final RedisService redisService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${airlabs.api.key}")
    private String airlabsApiKey;

    @Value("${airlabs.api.url}")
    private String airlabsBaseUrl;

    // Cache TTL: 2 minutes (120 seconds)
    private static final int CACHE_TTL_SECONDS = 120;

    // Stale threshold: 30 seconds
    private static final int STALE_THRESHOLD_SECONDS = 30;

    /*
     * ===========================================================
     * PUBLIC API - Cache Strategy (0-30s fresh, 30-120s stale+refresh, >120s expired)
     * ============================================================
     */

    /**
     * Fetch both departures and arrivals with intelligent cache strategy
     */
    public Map<String, List<Airline>> fetchAndSaveAllFlights(String iata) {
        String redisKey = "FLIGHTS:" + iata.toUpperCase();
        CachedData cached = redisService.getFlightsWithTimestamp(redisKey);

        if (cached != null) {
            long age = cached.getAgeSeconds();
            if (age < STALE_THRESHOLD_SECONDS) {
                log.debug("✅ Fresh cache: {}", redisKey); // Giảm mức log xuống debug để ít overhead hơn
                return cached.getData();
            }
            if (age < CACHE_TTL_SECONDS) {
                log.debug("⚡ Stale cache → background refresh: {}", redisKey);
                refreshAllFlightsAsync(iata);
                return cached.getData();
            }
            // expired → xóa và fetch mới
            redisService.deleteFlights(redisKey);
        }

        // Cache miss / expired → fetch mới
        log.info("🔄 Cache miss: {}", redisKey); // Giữ info cho cache miss vì quan trọng

        String depUrl = buildUrl("dep_iata", iata);
        String arrUrl = buildUrl("arr_iata", iata);

        // Gọi song song hai syncFlights bằng CompletableFuture để giảm thời gian chờ
        CompletableFuture<Void> depFuture = CompletableFuture.runAsync(() -> syncFlights(depUrl, true));
        CompletableFuture<Void> arrFuture = CompletableFuture.runAsync(() -> syncFlights(arrUrl, false));

        // Chờ cả hai hoàn thành
        CompletableFuture.allOf(depFuture, arrFuture).join();

        // Lấy từ DB
        List<Airline> departures = airlineRepository.findByDepIata(iata);
        List<Airline> arrivals = airlineRepository.findByArrIata(iata);

        Map<String, List<Airline>> result = new HashMap<>();
        result.put("departures", departures);
        result.put("arrivals", arrivals);

        redisService.saveFlightsWithTTL(redisKey, result, CACHE_TTL_SECONDS);

        return result;
    }

    /*
     * ===========================================================
     * ASYNC BACKGROUND REFRESH
     * ============================================================
     */

    /**
     * Background refresh for all flights (non-blocking)
     */
    @Async
    public void refreshAllFlightsAsync(String iata) {
        String lockKey = "LOCK:FLIGHTS:" + iata.toUpperCase();
        String token = redisService.acquireLock(lockKey, 60);
        if (token != null) {
            try {
                log.info("🔄 Background refresh started: FLIGHTS:{}", iata);
                String depUrl = buildUrl("dep_iata", iata);
                String arrUrl = buildUrl("arr_iata", iata);

                // Gọi song song trong async để tối ưu
                CompletableFuture<Void> depFuture = CompletableFuture.runAsync(() -> syncFlights(depUrl, true));
                CompletableFuture<Void> arrFuture = CompletableFuture.runAsync(() -> syncFlights(arrUrl, false));
                CompletableFuture.allOf(depFuture, arrFuture).join();

                List<Airline> departures = airlineRepository.findByDepIata(iata);
                List<Airline> arrivals = airlineRepository.findByArrIata(iata);

                Map<String, List<Airline>> result = new HashMap<>();
                result.put("departures", departures);
                result.put("arrivals", arrivals);

                String redisKey = "FLIGHTS:" + iata.toUpperCase();
                redisService.saveFlightsWithTTL(redisKey, result, CACHE_TTL_SECONDS);

                log.info("✅ Background refresh completed: FLIGHTS:{}", iata);
            } catch (Exception e) {
                log.error("❌ Background refresh failed: FLIGHTS:{} - {}", iata, e.getMessage(), e);
            } finally {
                redisService.releaseLock(lockKey, token);
            }
        } else {
            log.debug("🔒 Lock already held for FLIGHTS:{}", iata); // Tránh refresh trùng lặp
        }
    }

    /*
     * ===========================================================
     * CORE SYNC LOGIC
     * ============================================================
     */

    @Transactional(isolation = Isolation.READ_COMMITTED) // Giảm isolation xuống READ_COMMITTED để giảm khóa, tăng concurrency
    protected void syncFlights(String url, boolean isDeparture) {
        List<Airline> apiFlights = fetchFromApi(url);
        if (apiFlights.isEmpty()) return;

        // Lấy các flight code để query DB (tối ưu bằng Set để tránh duplicate)
        Set<String> flightCodes = new HashSet<>();
        for (Airline apiF : apiFlights) {
            if (apiF.getFlightIata() != null) {
                flightCodes.add(apiF.getFlightIata());
            }
        }

        // Load từ DB
        List<Airline> dbFlights = airlineRepository.findByFlightIataIn(flightCodes);
        Map<String, Airline> dbMap = new HashMap<>(dbFlights.size());
        for (Airline dbF : dbFlights) {
            String key = uniqueKey(dbF, isDeparture);
            dbMap.put(key, dbF);
        }

        // Thu thập batch cho insert/update/delete để sử dụng saveAll/deleteAll
        List<Airline> toInsert = new ArrayList<>();
        List<Airline> toUpdate = new ArrayList<>();
        Set<String> apiKeys = new HashSet<>(apiFlights.size());

        for (Airline apiF : apiFlights) {
            String key = uniqueKey(apiF, isDeparture);
            apiKeys.add(key);

            try {
                if (!dbMap.containsKey(key)) {
                    toInsert.add(apiF);
                } else {
                    Airline existing = dbMap.get(key);
                    if (isChanged(existing, apiF)) {
                        updateEntity(existing, apiF);
                        toUpdate.add(existing);
                    }
                }
            } catch (DataIntegrityViolationException e) {
                // Handle duplicate constraint (race condition) - ít xảy ra hơn với lock
                Airline existing = isDeparture ?
                        airlineRepository.findByFlightIataAndDepTime(apiF.getFlightIata(), apiF.getDepTime()) :
                        airlineRepository.findByFlightIataAndArrTime(apiF.getFlightIata(), apiF.getArrTime());
                if (existing != null && isChanged(existing, apiF)) {
                    updateEntity(existing, apiF);
                    toUpdate.add(existing);
                }
            }
        }

        // Batch save
        if (!toInsert.isEmpty()) {
            airlineRepository.saveAll(toInsert);
        }
        if (!toUpdate.isEmpty()) {
            airlineRepository.saveAll(toUpdate);
        }

        // Xóa flight không còn trong API
        List<Airline> toDelete = new ArrayList<>();
        for (Airline dbF : dbFlights) {
            String key = uniqueKey(dbF, isDeparture);
            if (!apiKeys.contains(key)) {
                toDelete.add(dbF);
            }
        }
        if (!toDelete.isEmpty()) {
            airlineRepository.deleteAll(toDelete);
        }
    }

    /*
     * ===========================================================
     * FETCH API
     * ============================================================
     */

    private List<Airline> fetchFromApi(String url) {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> body = response.getBody();

            if (body == null || !body.containsKey("response")) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("response");

            // Tối ưu stream bằng parallel nếu dataset lớn, nhưng giữ sequential vì thường không quá lớn
            return data.stream()
                    .map(this::mapToEntity)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception ex) {
            log.error("API call failed: {}", ex.getMessage(), ex);
            throw new RuntimeException("Lỗi khi gọi API: " + ex.getMessage(), ex);
        }
    }

    /*
     * ===========================================================
     * BUILD URL
     * ============================================================
     */

    private String buildUrl(String key, String value) {
        return UriComponentsBuilder.fromUriString(airlabsBaseUrl + "/schedules")
                .queryParam("api_key", airlabsApiKey)
                .queryParam(key, value)
                .toUriString();
    }

    /*
     * ===========================================================
     * UNIQUE KEY
     * ============================================================
     */

    private String uniqueKey(Airline a, boolean isDeparture) {
        return isDeparture
                ? "DEP:" + a.getFlightIata() + "_" + a.getDepTime()
                : "ARR:" + a.getFlightIata() + "_" + a.getArrTime();
    }

    /*
     * ===========================================================
     * DETECT CHANGES - Sử dụng hash thay vì so sánh từng trường
     * ============================================================
     */

    private boolean isChanged(Airline old, Airline fresh) {
        return computeChangeableHash(old) != computeChangeableHash(fresh);
    }

    private int computeChangeableHash(Airline a) {
        // Sử dụng Objects.hash cho các trường có thể thay đổi, hiệu quả hơn so sánh từng cái
        return Objects.hash(
                a.getDepGate(),
                a.getDepActual(),
                a.getArrGate(),
                a.getArrActual(),
                a.getStatus(),
                a.getDelayed()
        );
    }

    /*
     * ===========================================================
     * UPDATE ENTITY
     * ============================================================
     */

    private void updateEntity(Airline old, Airline fresh) {
        old.setDepGate(fresh.getDepGate());
        old.setDepActual(fresh.getDepActual());
        old.setArrGate(fresh.getArrGate());
        old.setArrActual(fresh.getArrActual());
        old.setStatus(fresh.getStatus());
        old.setDelayed(fresh.getDelayed());
    }

    /*
     * ===========================================================
     * MAP JSON → ENTITY
     * ============================================================
     */

    private Airline mapToEntity(Map<String, Object> m) {
        try {
            String flightIata = (String) m.get("flight_iata");
            String depIata = (String) m.get("dep_iata");
            String arrIata = (String) m.get("arr_iata");

            if (flightIata == null || depIata == null || arrIata == null)
                return null;

            Airline a = new Airline();

            a.setAirlineIata((String) m.get("airline_iata"));
            a.setFlightIata(flightIata);
            a.setFlightNumber((String) m.get("flight_number"));

            a.setDepIata(depIata);
            a.setDepTerminal((String) m.get("dep_terminal"));
            a.setDepGate((String) m.get("dep_gate"));
            a.setDepTime((String) m.get("dep_time"));
            a.setDepActual((String) m.get("dep_actual"));

            a.setArrIata(arrIata);
            a.setArrTerminal((String) m.get("arr_terminal"));
            a.setArrGate((String) m.get("arr_gate"));
            a.setArrTime((String) m.get("arr_time"));
            a.setArrActual((String) m.get("arr_actual"));

            a.setStatus((String) m.get("status"));

            if (m.get("duration") != null)
                a.setDuration(((Number) m.get("duration")).intValue());

            if (m.get("delayed") != null)
                a.setDelayed(((Number) m.get("delayed")).intValue());

            return a;

        } catch (Exception e) {
            log.error("Failed to map entity: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Wrapper class for cached data with timestamp
     */
    public static class CachedData {
        private final Map<String, List<Airline>> data;
        private final long timestamp;

        public CachedData(Map<String, List<Airline>> data, long timestamp) {
            this.data = data;
            this.timestamp = timestamp;
        }

        public Map<String, List<Airline>> getData() {
            return data;
        }

        public long getAgeSeconds() {
            return (System.currentTimeMillis() - timestamp) / 1000;
        }
    }
}