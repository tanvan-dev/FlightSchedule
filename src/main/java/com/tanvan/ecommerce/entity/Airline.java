package com.tanvan.ecommerce.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(
        name = "airline_schedule",
        indexes = {
                @Index(name = "idx_dep_iata", columnList = "dep_iata"),
                @Index(name = "idx_arr_iata", columnList = "arr_iata")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "unique_departure",
                        columnNames = {"flight_iata", "dep_time"}
                ),
                @UniqueConstraint(
                        name = "unique_arrival",
                        columnNames = {"flight_iata", "arr_time"}
                )
        }
)
@Data
public class Airline {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    // ======= Flight info =======
    @Column(name = "flight_number")
    private String flightNumber;

    @Column(name = "flight_iata")
    private String flightIata;    // Quan trọng nhất!

    @Column(name = "airline_iata")
    private String airlineIata;

    // ======= Departure =======
    @Column(name = "dep_iata")
    private String depIata;

    @Column(name = "dep_terminal")
    private String depTerminal;

    @Column(name = "dep_gate")
    private String depGate;

    @Column(name = "dep_time")
    private String depTime;

    @Column(name = "dep_actual")
    private String depActual;

    // ======= Arrival =======
    @Column(name = "arr_iata")
    private String arrIata;

    @Column(name = "arr_terminal")
    private String arrTerminal;

    @Column(name = "arr_gate")
    private String arrGate;

    @Column(name = "arr_time")
    private String arrTime;

    @Column(name = "arr_actual")
    private String arrActual;

    // ======= Other =======
    private String status;

    private Integer duration;
    private Integer delayed;
}