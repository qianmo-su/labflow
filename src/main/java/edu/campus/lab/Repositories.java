package edu.campus.lab;

import java.time.LocalDate;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface LabRepo extends JpaRepository<Lab,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from Lab r where r.id=:id") Optional<Lab> lock(@Param("id")long id);
}
interface BookingRepo extends JpaRepository<Booking,Long> {
    @Query("select b.labId from Booking b where b.id=:id") Optional<Long> roomOf(@Param("id")long id);
    List<Booking> findByOwnerOrderByIdDesc(String owner);
    List<Booking> findAllByOrderByIdDesc();
}
interface SlotRepo extends JpaRepository<Slot,Long> {
    List<Slot> findByLabIdAndDayBetween(long labId,LocalDate from,LocalDate to);
    List<Slot> findByLabIdAndDayGreaterThanEqual(long labId,LocalDate from);
    void deleteByBookingId(long bookingId);
}
