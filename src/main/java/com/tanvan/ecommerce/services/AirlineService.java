package com.tanvan.ecommerce.services;

import com.tanvan.ecommerce.entity.Airline;
import com.tanvan.ecommerce.repository.AirlineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AirlineService {

    private final AirlineRepository airlineRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${airlabs.api.key}")
    private String airlabsApiKey;

    @Value("${airlabs.api.url}")
    private String airlabsBaseUrl;

    /*
     * ===========================================================
     * PUBLIC API - fetch API → sync DB → trả về data từ DB
     * ============================================================
     */

    public List<Airline> fetchAndSaveDepartures(String depIata) {
        // Gọi API và sync database
        String url = buildUrl("dep_iata", depIata);
        syncFlights(url, true);

        // Trả về dữ liệu từ DB
        return airlineRepository.findByDepIata(depIata);
    }

    public List<Airline> fetchAndSaveArrivals(String arrIata) {
        String url = buildUrl("arr_iata", arrIata);
        syncFlights(url, false);

        return airlineRepository.findByArrIata(arrIata);
    }

    public Map<String, List<Airline>> fetchAndSaveAllFlights(String iata) {
        List<Airline> departures = fetchAndSaveDepartures(iata);
        List<Airline> arrivals = fetchAndSaveArrivals(iata);

        Map<String, List<Airline>> result = new HashMap<>();
        result.put("departures", departures);
        result.put("arrivals", arrivals);

        return result;
    }

    /*
     * ===========================================================
     * CORE SYNC LOGIC
     * ============================================================
     */

    @Transactional(isolation = Isolation.SERIALIZABLE)
    private void syncFlights(String url, boolean isDeparture) {
        List<Airline> apiFlights = fetchFromApi(url);
        if (apiFlights.isEmpty()) return;

        // Lấy các flight code để query DB
        Set<String> flightCodes = apiFlights.stream()
                .map(Airline::getFlightIata)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Load từ DB
        List<Airline> dbFlights = airlineRepository.findByFlightIataIn(flightCodes);

        // Tạo map lookup
        Map<String, Airline> dbMap = dbFlights.stream()
                .collect(Collectors.toMap(
                        f -> uniqueKey(f, isDeparture),
                        f -> f,
                        (a, b) -> a
                ));

        // Insert hoặc Update
        for (Airline apiF : apiFlights) {
            String key = uniqueKey(apiF, isDeparture);

            if (!dbMap.containsKey(key)) {
                airlineRepository.save(apiF);
            } else {
                Airline existing = dbMap.get(key);
                if (isChanged(existing, apiF)) {
                    updateEntity(existing, apiF);
                    airlineRepository.save(existing);
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
            return null;
        }
    }
}