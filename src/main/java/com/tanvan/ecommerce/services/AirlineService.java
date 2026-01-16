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
     * Fetch departures with intelligent cache strategy
     * 0-30s: Return fresh cache
     * 30-120s: Return stale cache + background refresh
     * >120s: Fetch new from API
     */
//    public List<Airline> fetchAndSaveDepartures(String depIata) {
//        String redisKey = "DEPARTURES:" + depIata.toUpperCase();
//
//        // 1️⃣ Check Redis with timestamp
//        CachedData cachedData = redisService.getFlightsWithTimestamp(redisKey);
//
//        if (cachedData != null) {
//            long ageSeconds = cachedData.getAgeSeconds();
//
//            // Fresh cache (0-30s) ✅
//            if (ageSeconds < STALE_THRESHOLD_SECONDS) {
//                log.info("✅ Cache HIT (fresh): DEPARTURES:{} - age: {}s", depIata, ageSeconds);
//                return extractDepartures(cachedData.getData());
//            }
//
//            // Stale cache (30-120s) ⚡ - return + refresh background
//            if (ageSeconds < CACHE_TTL_SECONDS) {
//                log.info("⚡ Cache HIT (stale): DEPARTURES:{} - age: {}s, refreshing background", depIata, ageSeconds);
//                refreshDeparturesAsync(depIata);
//                return extractDepartures(cachedData.getData());
//            }
//
//            // Expired cache (>120s) 🔄 - delete and fetch fresh
//            log.info("❌ Cache EXPIRED: DEPARTURES:{} - age: {}s, fetching new", depIata, ageSeconds);
//            redisService.deleteFlights(redisKey);
//        }
//
//        // 2️⃣ Cache MISS or EXPIRED → Sync with DB
//        log.info("🔄 Cache MISS: DEPARTURES:{} - fetching from API", depIata);
//        String url = buildUrl("dep_iata", depIata);
//        syncFlights(url, true);
//
//        // 3️⃣ Save to cache
//        List<Airline> departures = airlineRepository.findByDepIata(depIata);
//        Map<String, List<Airline>> cacheData = new HashMap<>();
//        cacheData.put("departures", departures);
//        redisService.saveFlightsWithTTL(redisKey, cacheData, CACHE_TTL_SECONDS);
//
//        return departures;
//    }

    /**
     * Fetch arrivals with intelligent cache strategy
     * 0-30s: Return fresh cache
     * 30-120s: Return stale cache + background refresh
     * >120s: Fetch new from API
     */
//    public List<Airline> fetchAndSaveArrivals(String arrIata) {
//        String redisKey = "ARRIVALS:" + arrIata.toUpperCase();
//
//        // 1️⃣ Check Redis with timestamp
//        CachedData cachedData = redisService.getFlightsWithTimestamp(redisKey);
//
//        if (cachedData != null) {
//            long ageSeconds = cachedData.getAgeSeconds();
//
//            // Fresh cache (0-30s) ✅
//            if (ageSeconds < STALE_THRESHOLD_SECONDS) {
//                log.info("✅ Cache HIT (fresh): ARRIVALS:{} - age: {}s", arrIata, ageSeconds);
//                return extractArrivals(cachedData.getData());
//            }
//
//            // Stale cache (30-120s) ⚡ - return + refresh background
//            if (ageSeconds < CACHE_TTL_SECONDS) {
//                log.info("⚡ Cache HIT (stale): ARRIVALS:{} - age: {}s, refreshing background", arrIata, ageSeconds);
//                refreshArrivalsAsync(arrIata);
//                return extractArrivals(cachedData.getData());
//            }
//
//            // Expired cache (>120s) 🔄 - delete and fetch fresh
//            log.info("❌ Cache EXPIRED: ARRIVALS:{} - age: {}s, fetching new", arrIata, ageSeconds);
//            redisService.deleteFlights(redisKey);
//        }
//
//        // 2️⃣ Cache MISS or EXPIRED → Sync with DB
//        log.info("🔄 Cache MISS: ARRIVALS:{} - fetching from API", arrIata);
//        String url = buildUrl("arr_iata", arrIata);
//        syncFlights(url, false);
//
//        // 3️⃣ Save to cache
//        List<Airline> arrivals = airlineRepository.findByArrIata(arrIata);
//        Map<String, List<Airline>> cacheData = new HashMap<>();
//        cacheData.put("arrivals", arrivals);
//        redisService.saveFlightsWithTTL(redisKey, cacheData, CACHE_TTL_SECONDS);
//
//        return arrivals;
//    }

    /**
     * Fetch both departures and arrivals with intelligent cache strategy
     */
    public Map<String, List<Airline>> fetchAndSaveAllFlights(String iata) {
        String redisKey = "FLIGHTS:" + iata.toUpperCase();
        CachedData cached = redisService.getFlightsWithTimestamp(redisKey);

        if (cached != null) {
            long age = cached.getAgeSeconds();
            if (age < STALE_THRESHOLD_SECONDS) {
                log.info("✅ Fresh cache: {}", redisKey);
                return cached.getData();
            }
            if (age < CACHE_TTL_SECONDS) {
                log.info("⚡ Stale cache → background refresh: {}", redisKey);
                refreshAllFlightsAsync(iata);
                return cached.getData();
            }
            // expired → xóa và fetch mới
            redisService.deleteFlights(redisKey);
        }

        // Cache miss / expired → fetch mới
        log.info("🔄 Cache miss: {}", redisKey);

        String depUrl = buildUrl("dep_iata", iata);
        String arrUrl = buildUrl("arr_iata", iata);

        // Gọi đồng bộ cả hai
        syncFlights(depUrl, true);
        syncFlights(arrUrl, false);

        // Lấy từ DB
        List<Airline> departures = airlineRepository.findByDepIata(iata);
        List<Airline> arrivals   = airlineRepository.findByArrIata(iata);

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
     * Background refresh for departures (non-blocking)
     */
//    @Async
//    public void refreshDeparturesAsync(String depIata) {
//        try {
//            log.info("🔄 Background refresh started: DEPARTURES:{}", depIata);
//            String url = buildUrl("dep_iata", depIata);
//            syncFlights(url, true);
//
//            // Update cache
//            List<Airline> departures = airlineRepository.findByDepIata(depIata);
//            Map<String, List<Airline>> cacheData = new HashMap<>();
//            cacheData.put("departures", departures);
//            String redisKey = "DEPARTURES:" + depIata.toUpperCase();
//            redisService.saveFlightsWithTTL(redisKey, cacheData, CACHE_TTL_SECONDS);
//
//            log.info("✅ Background refresh completed: DEPARTURES:{}", depIata);
//        } catch (Exception e) {
//            log.error("❌ Background refresh failed: DEPARTURES:{} - {}", depIata, e.getMessage(), e);
//        }
//    }

    /**
     * Background refresh for arrivals (non-blocking)
     */
//    @Async
//    public void refreshArrivalsAsync(String arrIata) {
//        try {
//            log.info("🔄 Background refresh started: ARRIVALS:{}", arrIata);
//            String url = buildUrl("arr_iata", arrIata);
//            syncFlights(url, false);
//
//            // Update cache
//            List<Airline> arrivals = airlineRepository.findByArrIata(arrIata);
//            Map<String, List<Airline>> cacheData = new HashMap<>();
//            cacheData.put("arrivals", arrivals);
//            String redisKey = "ARRIVALS:" + arrIata.toUpperCase();
//            redisService.saveFlightsWithTTL(redisKey, cacheData, CACHE_TTL_SECONDS);
//
//            log.info("✅ Background refresh completed: ARRIVALS:{}", arrIata);
//        } catch (Exception e) {
//            log.error("❌ Background refresh failed: ARRIVALS:{} - {}", arrIata, e.getMessage(), e);
//        }
//    }

    /**
     * Background refresh for all flights (non-blocking)
     */
    @Async
    public void refreshAllFlightsAsync(String iata) {
        try {
            log.info("🔄 Background refresh started: FLIGHTS:{}", iata);
            String depUrl = buildUrl("dep_iata", iata);
            String arrUrl = buildUrl("arr_iata", iata);

            syncFlights(depUrl, true);
            syncFlights(arrUrl, false);

            List<Airline> departures = airlineRepository.findByDepIata(iata);
            List<Airline> arrivals   = airlineRepository.findByArrIata(iata);

            Map<String, List<Airline>> result = new HashMap<>();
            result.put("departures", departures);
            result.put("arrivals", arrivals);

            String redisKey = "FLIGHTS:" + iata.toUpperCase();
            redisService.saveFlightsWithTTL(redisKey, result, CACHE_TTL_SECONDS);

            log.info("✅ Background refresh completed: FLIGHTS:{}", iata);
        } catch (Exception e) {
            log.error("❌ Background refresh failed: FLIGHTS:{} - {}", iata, e.getMessage(), e);
        }
    }

    /*
     * ===========================================================
     * CORE SYNC LOGIC
     * ============================================================
     */

    @Transactional(isolation = Isolation.SERIALIZABLE)
    protected void syncFlights(String url, boolean isDeparture) {
        List<Airline> apiFlights = fetchFromApi(url);
        if (apiFlights.isEmpty()) return;

        // Lấy các flight code để query DB
        Set<String> flightCodes = apiFlights.stream()
                .map(Airline::getFlightIata)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Load từ DB
        List<Airline> dbFlights = airlineRepository.findByFlightIataIn(flightCodes);
        Map<String, Airline> dbMap = dbFlights.stream()
                .collect(Collectors.toMap(
                        f -> uniqueKey(f, isDeparture),
                        f -> f,
                        (a, b) -> a
                ));

        // Insert hoặc Update
        for (Airline apiF : apiFlights) {
            String key = uniqueKey(apiF, isDeparture);

            try {
                if (!dbMap.containsKey(key)) {
                    airlineRepository.save(apiF);
                } else {
                    Airline existing = dbMap.get(key);
                    if (isChanged(existing, apiF)) {
                        updateEntity(existing, apiF);
                        airlineRepository.save(existing);
                    }
                }
            } catch (DataIntegrityViolationException e) {
                // Handle duplicate constraint (race condition)
                if (isDeparture) {
                    Airline existing = airlineRepository.findByFlightIataAndDepTime(apiF.getFlightIata(), apiF.getDepTime());
                    if (existing != null && isChanged(existing, apiF)) {
                        updateEntity(existing, apiF);
                        airlineRepository.save(existing);
                    }
                } else {
                    Airline existing = airlineRepository.findByFlightIataAndArrTime(apiF.getFlightIata(), apiF.getArrTime());
                    if (existing != null && isChanged(existing, apiF)) {
                        updateEntity(existing, apiF);
                        airlineRepository.save(existing);
                    }
                }
            }
        }

        // Xóa flight không còn trong API
        Set<String> apiKeys = apiFlights.stream()
                .map(f -> uniqueKey(f, isDeparture))
                .collect(Collectors.toSet());

        for (Airline dbF : dbFlights) {
            String key = uniqueKey(dbF, isDeparture);
            if (!apiKeys.contains(key)) {
                airlineRepository.delete(dbF);
            }
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
     * DETECT CHANGES
     * ============================================================
     */

    private boolean isChanged(Airline old, Airline fresh) {
        return !Objects.equals(old.getDepGate(), fresh.getDepGate()) ||
                !Objects.equals(old.getDepActual(), fresh.getDepActual()) ||
                !Objects.equals(old.getArrGate(), fresh.getArrGate()) ||
                !Objects.equals(old.getArrActual(), fresh.getArrActual()) ||
                !Objects.equals(old.getStatus(), fresh.getStatus()) ||
                !Objects.equals(old.getDelayed(), fresh.getDelayed());
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

    /*
     * ===========================================================
     * HELPER: Extract data from cache
     * ============================================================
     */

//    private List<Airline> extractDepartures(Map<String, List<Airline>> data) {
//        List<?> deps = data.get("departures");
//        if (deps != null && !deps.isEmpty()) {
//            return (List<Airline>) deps;
//        }
//        return Collections.emptyList();
//    }

//    private List<Airline> extractArrivals(Map<String, List<Airline>> data) {
//        List<?> arrs = data.get("arrivals");
//        if (arrs != null && !arrs.isEmpty()) {
//            return (List<Airline>) arrs;
//        }
//        return Collections.emptyList();
//    }

    /*
     * ===========================================================
     * CACHED DATA CLASS
     * ============================================================
     */

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
