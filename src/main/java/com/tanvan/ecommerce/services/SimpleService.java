package com.tanvan.ecommerce.services;

import com.tanvan.ecommerce.entity.Airline;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

@Service
@RequiredArgsConstructor
public class SimpleService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${airlabs.api.key}")
    private String airlabsApiKey;

    @Value("${airlabs.api.url}")
    private String airlabsBaseUrl;

    /**
     * Chỉ fetch DEPARTURES từ API
     */
    public List<Airline> getDepartures(String depIata) {
        String url = buildUrl("dep_iata", depIata);
        return fetchFromApi(url);
    }

    /**
     * Chỉ fetch ARRIVALS từ API
     */
    public List<Airline> getArrivals(String arrIata) {
        String url = buildUrl("arr_iata", arrIata);
        return fetchFromApi(url);
    }

    /**
     * Fetch cả ARRIVALS + DEPARTURES
     */
    public Map<String, List<Airline>> getAllFlights(String iata) {
        Map<String, List<Airline>> map = new HashMap<>();
        map.put("departures", getDepartures(iata));
        map.put("arrivals", getArrivals(iata));
        return map;
    }

    /* ==================================================================
       PRIVATE METHODS
    ================================================================== */

    private String buildUrl(String key, String value) {
        return UriComponentsBuilder.fromUriString(airlabsBaseUrl + "/schedules")
                .queryParam("api_key", airlabsApiKey)
                .queryParam(key, value)
                .toUriString();
    }

    private List<Airline> fetchFromApi(String url) {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> body = response.getBody();

            if (body == null || !body.containsKey("response")) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> data =
                    (List<Map<String, Object>>) body.get("response");

            List<Airline> flights = new ArrayList<>();

            for (Map<String, Object> m : data) {
                Airline a = mapToEntity(m);
                if (a != null) flights.add(a);
            }

            return flights;

        } catch (Exception ex) {
            throw new RuntimeException("Lỗi khi gọi API: " + ex.getMessage(), ex);
        }
    }

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
