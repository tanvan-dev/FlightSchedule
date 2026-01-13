package com.tanvan.ecommerce.services;

import com.tanvan.ecommerce.entity.Airline;
import com.tanvan.ecommerce.repository.AirlineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

    private static final String CACHE_PREFIX = "flights:";
    private static final long CACHE_TTL = 30;

    /* ===========================================================
       PUBLIC API - Luôn trả về data sau khi sync
    ============================================================ */

    /**
     * Fetch DEPARTURES từ API → Sync DB → Trả về kết quả từ DB
     */
    @Cacheable(value = "departures", key = "#depIata")
    public List<Airline> fetchAndSaveDepartures(String depIata) {
        String cacheKey = CACHE_PREFIX + "dep:" + depIata;

        // Check Redis cache
        List<Airline> cached = (List<Airline>) redisService.get(cacheKey);
        if (cached != null) {
            System.out.println("✅ Cache HIT: " + cacheKey);
            return cached;
        }

        System.out.println("❌ Cache MISS: " + cacheKey);

        String url = buildUrl("dep_iata", depIata);
        syncFlights(url, true);

        // Get từ DB
        List<Airline> result = airlineRepository.findByDepIata(depIata);

        // Save to Redis
        redisService.set(cacheKey, result, CACHE_TTL, TimeUnit.MINUTES);

        // Trả về data từ DB sau khi sync (đảm bảo data nhất quán)
        return airlineRepository.findByDepIata(depIata);
    }

    /**
     * Fetch ARRIVALS từ API → Sync DB → Trả về kết quả từ DB
     */
    @Cacheable(value = "arrivals", key = "#arrIata")
    public List<Airline> fetchAndSaveArrivals(String arrIata) {
        String cacheKey = CACHE_PREFIX + "arr:" + arrIata;

        List<Airline> cached = (List<Airline>) redisService.get(cacheKey);
        if (cached != null) {
            System.out.println("✅ Cache HIT: " + cacheKey);
            return cached;
        }

        System.out.println("❌ Cache MISS: " + cacheKey);

        String url = buildUrl("arr_iata", arrIata);
        syncFlights(url, false);

        List<Airline> result = airlineRepository.findByArrIata(arrIata);
        redisService.set(cacheKey, result, CACHE_TTL, TimeUnit.MINUTES);

        return result;
    }

    /**
     * Fetch CẢ HAI (Departures + Arrivals) → Sync → Trả về
     */
    public Map<String, List<Airline>> fetchAndSaveAllFlights(String iata) {
        String cacheKey = CACHE_PREFIX + "all:" + iata;

        // Try get from cache
        Map<String, List<Airline>> cached =
                (Map<String, List<Airline>>) redisService.get(cacheKey);

        if (cached != null) {
            System.out.println("✅ Cache HIT (ALL): " + cacheKey);
            return cached;
        }

        System.out.println("❌ Cache MISS (ALL): " + cacheKey);

        // Fetch both
        List<Airline> departures = fetchAndSaveDepartures(iata);
        List<Airline> arrivals = fetchAndSaveArrivals(iata);

        Map<String, List<Airline>> result = new HashMap<>();
        result.put("departures", departures);
        result.put("arrivals", arrivals);

        // Cache result
        redisService.set(cacheKey, result, CACHE_TTL, TimeUnit.MINUTES);

        return result;
    }

    /**
     * Chỉ lấy từ DB (không gọi API) - Dùng cho cached endpoint
     */
    public Map<String, List<Airline>> getFlightsFromDatabase(String iata) {
        String cacheKey = CACHE_PREFIX + "db:" + iata;

        Map<String, List<Airline>> cached =
                (Map<String, List<Airline>>) redisService.get(cacheKey);

        if (cached != null) {
            return cached;
        }

        Map<String, List<Airline>> result = new HashMap<>();
        result.put("departures", airlineRepository.findByDepIata(iata));
        result.put("arrivals", airlineRepository.findByArrIata(iata));

        redisService.set(cacheKey, result, CACHE_TTL, TimeUnit.MINUTES);

        return result;
    }

    /**
     * Clear cache cho một airport
     */
    @CacheEvict(value = {"departures", "arrivals"}, key = "#iata")
    public void clearCache(String iata) {
        redisService.deleteByPattern(CACHE_PREFIX + "*:" + iata);
        System.out.println("🗑️ Cleared cache for: " + iata);
    }

    /**
     * Clear ALL cache
     */
    @CacheEvict(value = {"departures", "arrivals"}, allEntries = true)
    public void clearAllCache() {
        redisService.deleteByPattern(CACHE_PREFIX + "*");
        System.out.println("🗑️ Cleared ALL cache");
    }

    /* ===========================================================
       CORE SYNC LOGIC - Insert hoặc Update
    ============================================================ */

    private void syncFlights(String url, boolean isDeparture) {
        // 1. Fetch từ API
        List<Airline> apiFlights = fetchFromApi(url);
        if (apiFlights.isEmpty()) return;

        // 2. Lấy flight codes để query DB
        Set<String> flightCodes = apiFlights.stream()
                .map(Airline::getFlightIata)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 3. Load existing flights từ DB
        List<Airline> dbFlights = airlineRepository.findByFlightIataIn(flightCodes);

        // 4. Tạo map để lookup nhanh
        Map<String, Airline> dbMap = dbFlights.stream()
                .collect(Collectors.toMap(
                        f -> uniqueKey(f, isDeparture),
                        f -> f,
                        (a, b) -> a // Keep first if duplicate
                ));

        // 5. Sync từng flight: Insert mới hoặc Update
        for (Airline apiF : apiFlights) {

            // QUAN TRỌNG: Với ARRIVAL, set depTime = null để tránh conflict
            if (!isDeparture) {
                apiF.setDepTime(null);
            }

            String key = uniqueKey(apiF, isDeparture);

            if (!dbMap.containsKey(key)) {
                // ✅ not have in database → INSERT
                airlineRepository.save(apiF);
            } else {
                // ✅ Đã có trong DB → UPDATE nếu có thay đổi
                Airline existingFlight = dbMap.get(key);

                if (isChanged(existingFlight, apiF)) {
                    updateEntity(existingFlight, apiF);
                    airlineRepository.save(existingFlight);
                }
            }
        }
    }

    /* ===========================================================
       FETCH FROM API
    ============================================================ */

    private List<Airline> fetchFromApi(String url) {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> body = response.getBody();

            if (body == null || !body.containsKey("response")) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> data =
                    (List<Map<String, Object>>) body.get("response");

            return data.stream()
                    .map(this::mapToEntity)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception ex) {
            throw new RuntimeException("Lỗi khi gọi API: " + ex.getMessage(), ex);
        }
    }

    /* ===========================================================
       BUILD URL
    ============================================================ */

    private String buildUrl(String key, String value) {
        return UriComponentsBuilder.fromUriString(airlabsBaseUrl + "/schedules")
                .queryParam("api_key", airlabsApiKey)
                .queryParam(key, value)
                .toUriString();
    }

    /* ===========================================================
       UNIQUE KEY - Phân biệt Departure vs Arrival
    ============================================================ */

    private String uniqueKey(Airline a, boolean isDeparture) {
        // Departure: flight_iata + dep_time
        // Arrival: flight_iata + arr_time
        return isDeparture
                ? a.getFlightIata() + "_" + a.getDepTime()
                : a.getFlightIata() + "_" + a.getArrTime();
    }

    /* ===========================================================
       CHECK CHANGES - Chỉ update khi có thay đổi thực sự
    ============================================================ */

    private boolean isChanged(Airline old, Airline fresh) {
        return !Objects.equals(old.getDepGate(), fresh.getDepGate()) ||
                !Objects.equals(old.getDepActual(), fresh.getDepActual()) ||
                !Objects.equals(old.getArrGate(), fresh.getArrGate()) ||
                !Objects.equals(old.getArrActual(), fresh.getArrActual()) ||
                !Objects.equals(old.getStatus(), fresh.getStatus()) ||
                !Objects.equals(old.getDelayed(), fresh.getDelayed());
    }

    /* ===========================================================
       UPDATE ENTITY - Chỉ update các trường có thể thay đổi
    ============================================================ */

    private void updateEntity(Airline old, Airline fresh) {
        old.setDepGate(fresh.getDepGate());
        old.setDepActual(fresh.getDepActual());
        old.setArrGate(fresh.getArrGate());
        old.setArrActual(fresh.getArrActual());
        old.setStatus(fresh.getStatus());
        old.setDelayed(fresh.getDelayed());
        // Không update dep_time, arr_time, flight_iata (là key)
    }

    /* ===========================================================
       MAP JSON → ENTITY
    ============================================================ */

    private Airline mapToEntity(Map<String, Object> m) {
        try {
            Airline a = new Airline();

            a.setAirlineIata((String) m.get("airline_iata"));
            a.setFlightIata((String) m.get("flight_iata"));
            a.setFlightNumber((String) m.get("flight_number"));

            a.setDepIata((String) m.get("dep_iata"));
            a.setDepTerminal((String) m.get("dep_terminal"));
            a.setDepGate((String) m.get("dep_gate"));
            a.setDepTime((String) m.get("dep_time"));
            a.setDepActual((String) m.get("dep_actual"));

            a.setArrIata((String) m.get("arr_iata"));
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
            System.err.println("[Mapping error] " + e.getMessage());
            return null;
        }
    }
}