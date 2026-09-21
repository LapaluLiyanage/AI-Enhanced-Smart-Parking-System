package com.smartparking.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "locations")
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private int totalSlots;

    protected Location() {}

    public Location(String name, String address, int totalSlots) {
        this.name = name;
        this.address = address;
        this.totalSlots = totalSlots;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public int getTotalSlots() { return totalSlots; }
}
