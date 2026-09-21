package com.smartparking.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "parking_slots")
public class ParkingSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "location_id")
    private Location location;

    @Column(nullable = false)
    private int slotNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SlotStatus status;

    protected ParkingSlot() {}

    public ParkingSlot(Location location, int slotNumber, SlotStatus status) {
        this.location = location;
        this.slotNumber = slotNumber;
        this.status = status;
    }

    public Long getId() { return id; }
    public Location getLocation() { return location; }
    public int getSlotNumber() { return slotNumber; }
    public SlotStatus getStatus() { return status; }
    public void setStatus(SlotStatus status) { this.status = status; }
}
