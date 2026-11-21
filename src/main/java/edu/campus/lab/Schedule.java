package edu.campus.lab;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service @Transactional(isolation=Isolation.READ_COMMITTED)
class Schedule {
    final LabRepo labs; final BookingRepo bookings; final SlotRepo slots;
    Schedule(LabRepo labs,BookingRepo bookings,SlotRepo slots){this.labs=labs;this.bookings=bookings;this.slots=slots;}
    private Lab room(Long id){
        if(id==null)throw new Rule(400,"请选择实验室");
        return labs.lock(id).orElseThrow(()->new Rule(404,"实验室不存在"));
    }
    private Booking lockedBooking(long id){
        // All mutations acquire the same room lock first. Read the booking state only after the lock.
        room(bookings.roomOf(id).orElseThrow(()->new Rule(404,"申请不存在")));
        return bookings.findById(id).orElseThrow();
    }
    static String text(String value,int max,String field){
        String s=value==null?"":value.strip();
        if(s.isEmpty()||s.length()>max)throw new Rule(400,field+"须填写 1 至 "+max+" 个字符");
        return s;
    }
    static void dates(LocalDate d){
        LocalDate today=LocalDate.now();
        if(d==null||d.isBefore(today)||d.isAfter(today.plusDays(90)))throw new Rule(400,"日期须在今天至未来 90 天内");
    }
    static void periods(int from,int to,int low,int high){
        if(from<low||to>high||from>to)throw new Rule(400,"节次须连续且在开放范围内（"+low+" 至 "+high+"）");
    }
    static void requirePending(Booking b){
        if(!"PENDING".equals(b.state))throw new Rule(409,"只能处理待审申请");
    }
    void roomFields(Lab r,RoomInput x){
        r.name=text(x.name(),60,"实验室名称");r.location=text(x.location(),80,"位置");
        if(x.seats()<1||x.seats()>500)throw new Rule(400,"容量范围为 1 至 500");
        periods(x.firstPeriod(),x.lastPeriod(),1,8);
        r.seats=x.seats();r.firstPeriod=x.firstPeriod();r.lastPeriod=x.lastPeriod();
    }
    public Lab addRoom(RoomInput x){Lab r=new Lab();roomFields(r,x);return labs.saveAndFlush(r);}
    public Lab editRoom(long id,RoomInput x){
        Lab r=room(id);
        if(slots.findByLabIdAndDayGreaterThanEqual(id,LocalDate.now()).stream().anyMatch(s->s.period<x.firstPeriod()||s.period>x.lastPeriod()))
            throw new Rule(409,"已有排期超出新的开放时段");
        roomFields(r,x);return labs.saveAndFlush(r);
    }
    private void available(long labId,LocalDate day,int a,int z){
        slots.findByLabIdAndDayBetween(labId,day,day).stream().filter(s->s.period>=a&&s.period<=z)
            .min(Comparator.comparingInt(s->s.period)).ifPresent(s->{throw new Rule(409,"第 "+s.period+" 节已被占用");});
    }
    public Booking apply(String owner,RequestInput x){
        Lab r=room(x.labId());dates(x.day());periods(x.firstPeriod(),x.lastPeriod(),r.firstPeriod,r.lastPeriod);
        available(r.id,x.day(),x.firstPeriod(),x.lastPeriod());
        Booking b=new Booking();b.labId=r.id;b.day=x.day();b.firstPeriod=x.firstPeriod();b.lastPeriod=x.lastPeriod();
        b.purpose=text(x.purpose(),160,"实验用途");b.owner=owner;b.state="PENDING";b.createdAt=LocalDateTime.now();
        return bookings.save(b);
    }
    private void occupy(long labId,LocalDate day,int a,int z,Long bookingId,String label){
        available(labId,day,a,z);
        for(int p=a;p<=z;p++){Slot s=new Slot();s.labId=labId;s.day=day;s.period=p;s.bookingId=bookingId;s.label=label;slots.save(s);}
        slots.flush();
    }
    public Booking review(long id,String teacher,ReviewInput input){
        Booking b=lockedBooking(id);requirePending(b);
        String note=input.opinion()==null?"":input.opinion().strip();
        if(input.approve()){
            dates(b.day);Lab r=labs.findById(b.labId).orElseThrow();
            periods(b.firstPeriod,b.lastPeriod,r.firstPeriod,r.lastPeriod);
            occupy(r.id,b.day,b.firstPeriod,b.lastPeriod,b.id,"已预约");
            note=note.isBlank()?"同意":text(note,160,"审批意见");
        }else note=text(note,160,"驳回原因");
        b.state=input.approve()?"APPROVED":"REJECTED";b.opinion=note;b.reviewer=teacher;b.reviewedAt=LocalDateTime.now();
        return b;
    }
    public void cancel(long id,String user){
        Booking b=lockedBooking(id);
        if(!b.owner.equals(user))throw new Rule(403,"只能取消自己的预约");
        if(!Set.of("PENDING","APPROVED").contains(b.state)||b.day.isBefore(LocalDate.now()))throw new Rule(409,"当前状态或日期不可取消");
        slots.deleteByBookingId(id);b.state="CANCELLED";
    }
    public void maintain(BlockInput x){
        Lab r=room(x.labId());dates(x.day());periods(x.firstPeriod(),x.lastPeriod(),r.firstPeriod,r.lastPeriod);
        occupy(r.id,x.day(),x.firstPeriod(),x.lastPeriod(),null,text(x.note(),100,"检修原因"));
    }
    public void release(BlockInput x){
        Lab r=room(x.labId());dates(x.day());periods(x.firstPeriod(),x.lastPeriod(),1,8);
        var list=slots.findByLabIdAndDayBetween(r.id,x.day(),x.day()).stream()
            .filter(s->s.period>=x.firstPeriod()&&s.period<=x.lastPeriod()&&s.bookingId==null).toList();
        if(list.size()!=x.lastPeriod()-x.firstPeriod()+1)throw new Rule(409,"所选范围并非全部为检修节次");
        slots.deleteAll(list);
    }
    public List<RoomStat> stats(LocalDate start){
        List<RoomStat> out=new ArrayList<>();
        for(Lab r:labs.findAll()){
            int[] count=new int[9];int reserved=0,blocked=0;
            for(Slot s:slots.findByLabIdAndDayBetween(r.id,start,start.plusDays(6))){
                if(s.bookingId==null)blocked++;else{reserved++;count[s.period]++;}
            }
            int cap=(r.lastPeriod-r.firstPeriod+1)*7-blocked;
            int max=Arrays.stream(count).max().orElse(0);List<Integer> peak=new ArrayList<>();
            for(int i=1;i<=8;i++)if(max>0&&count[i]==max)peak.add(i);
            out.add(new RoomStat(r.id,r.name,reserved,blocked,cap,cap>0?Math.round(1000.0*reserved/cap)/10.0:0,peak));
        }
        return out;
    }
}
