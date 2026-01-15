package com.tanvan.ecommerce.repository;

import com.tanvan.ecommerce.entity.Airline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;

public interface AirlineRepository extends JpaRepository<Airline, Long> {
    boolean existsByFlightIataAndDepTime(String flightNumber, String depTime);

    // Lấy chuyến bay ĐẾN theo sân bay đích
    List<Airline> findByArrIata(String arrIata);

    // Lấy chuyến bay ĐI theo sân bay xuất phát
    List<Airline> findByDepIata(String depIata);

    // Lấy TẤT CẢ chuyến bay liên quan đến một sân bay (cả đến và đi)
    List<Airline> findByDepIataOrArrIata(String depIata, String arrIata);

    List<Airline> findByFlightIataIn(Set<String> flightIatas);

    Airline findByFlightIataAndDepTime(String flightIata, String depTime);

    Airline findByFlightIataAndArrTime(String flightIata, String arrTime);
}
