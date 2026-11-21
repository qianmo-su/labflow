package edu.campus.lab;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity @Table(name="lab_room",uniqueConstraints=@UniqueConstraint(columnNames="name"))
class Lab {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false,length=60) public String name;
    @Column(nullable=false,length=80) public String location;
    public int seats, firstPeriod, lastPeriod;
}
@Entity @Table(name="lab_booking",indexes=@Index(columnList="lab_id,booking_day"))
class Booking {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(name="lab_id",nullable=false) public Long labId;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="lab_id",insertable=false,updatable=false)
    private Lab room;
    @Column(name="booking_day",nullable=false) public LocalDate day;
    public int firstPeriod,lastPeriod;
    @Column(nullable=false,length=32) public String owner;
    @Column(nullable=false,length=160) public String purpose;
    @Column(nullable=false,length=16) public String state;
    public String reviewer,opinion;
    public LocalDateTime createdAt,reviewedAt;
}
@Entity @Table(name="lab_slot",uniqueConstraints=@UniqueConstraint(name="uq_room_day_period",columnNames={"lab_id","slot_day","period_no"}))
class Slot {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(name="lab_id",nullable=false) public Long labId;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="lab_id",insertable=false,updatable=false)
    private Lab room;
    @Column(name="slot_day",nullable=false) public LocalDate day;
    @Column(name="period_no",nullable=false) public int period;
    @Column(name="booking_id") public Long bookingId;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="booking_id",insertable=false,updatable=false)
    private Booking booking;
    @Column(nullable=false,length=160) public String label;
}
record RoomInput(String name,String location,int seats,int firstPeriod,int lastPeriod){}
record RequestInput(Long labId,LocalDate day,int firstPeriod,int lastPeriod,String purpose){}
record ReviewInput(boolean approve,String opinion){}
record BlockInput(Long labId,LocalDate day,int firstPeriod,int lastPeriod,String note){}
record RoomStat(long id,String name,int reserved,int maintenance,int capacity,double utilization,List<Integer> peak){}
class Rule extends RuntimeException {
    final int status;
    Rule(int status,String message){super(message);this.status=status;}
}
