package edu.campus.lab;

import java.security.Principal;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api")
class Api {
    final Schedule service;final LabRepo labs;final SlotRepo slots;final BookingRepo bookings;
    Api(Schedule service,LabRepo labs,SlotRepo slots,BookingRepo bookings){
        this.service=service;this.labs=labs;this.slots=slots;this.bookings=bookings;
    }
    @GetMapping("/session") Object session(Authentication auth,CsrfToken csrf){
        String user=auth==null?"":auth.getName();
        return Map.of("user",user,"name",Access.NAMES.getOrDefault(user,"访客"),
            "role",auth==null?"GUEST":auth.getAuthorities().iterator().next().getAuthority().substring(5),
            "csrf",csrf.getToken(),"header",csrf.getHeaderName(),"today",LocalDate.now());
    }
    @GetMapping("/rooms") Object rooms(){return labs.findAll(org.springframework.data.domain.Sort.by("id"));}
    record Cell(LocalDate day,int period,String type,String label){}
    @GetMapping("/calendar") Object calendar(@RequestParam long room,@RequestParam LocalDate start){
        return slots.findByLabIdAndDayBetween(room,start,start.plusDays(6)).stream()
            .map(s->new Cell(s.day,s.period,s.bookingId==null?"maintenance":"reserved",s.label)).toList();
    }
    @GetMapping("/bookings") Object list(Authentication a){
        boolean student=a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("ROLE_STUDENT"));
        return student?bookings.findByOwnerOrderByIdDesc(a.getName()):bookings.findAllByOrderByIdDesc();
    }
    @PostMapping("/student/bookings") Object apply(Principal p,@RequestBody RequestInput in){return service.apply(p.getName(),in);}
    @PostMapping("/student/bookings/{id}/cancel") void cancel(Principal p,@PathVariable long id){service.cancel(id,p.getName());}
    @PostMapping("/teacher/bookings/{id}/review") Object review(Principal p,@PathVariable long id,@RequestBody ReviewInput in){return service.review(id,p.getName(),in);}
    @PostMapping("/admin/rooms") Object add(@RequestBody RoomInput in){return service.addRoom(in);}
    @PutMapping("/admin/rooms/{id}") Object edit(@PathVariable long id,@RequestBody RoomInput in){return service.editRoom(id,in);}
    @PostMapping("/admin/maintenance") void block(@RequestBody BlockInput in){service.maintain(in);}
    @PostMapping("/admin/maintenance/release") void release(@RequestBody BlockInput in){service.release(in);}
    @GetMapping("/admin/stats") Object stats(@RequestParam LocalDate start){return service.stats(start);}
    @ExceptionHandler(Rule.class) ResponseEntity<?> rule(Rule e){return ResponseEntity.status(e.status).body(Map.of("message",e.getMessage()));}
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> duplicate(){return ResponseEntity.status(409).body(Map.of("message","记录与现有名称或节次冲突"));}
}
