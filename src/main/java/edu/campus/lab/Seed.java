package edu.campus.lab;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component @ConditionalOnProperty(name="lab.seed",havingValue="true",matchIfMissing=true)
class Seed implements CommandLineRunner {
    final Schedule s;final LabRepo labs;
    Seed(Schedule s,LabRepo labs){this.s=s;this.labs=labs;}
    @Transactional(isolation=Isolation.READ_COMMITTED) public void run(String... args){
        if(labs.count()>0)return;
        Lab a=s.addRoom(new RoomInput("智能感知实验室","明理楼 A301",32,1,8));
        Lab b=s.addRoom(new RoomInput("网络工程实验室","明理楼 B205",48,1,8));
        s.addRoom(new RoomInput("嵌入式系统实验室","工学楼 C402",24,2,7));
        LocalDate mon=LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        for(int[] spec:new int[][]{{0,1,2},{1,5,6},{3,3,4}}){
            Booking q=s.apply("student2",new RequestInput(a.id,mon.plusDays(spec[0]),spec[1],spec[2],"传感器数据采集"));
            s.review(q.id,"teacher2",new ReviewInput(true,"同意排期"));
        }
        s.maintain(new BlockInput(a.id,mon.plusDays(4),5,6,"仪器校准"));
        s.apply("student1",new RequestInput(b.id,mon.plusDays(1),1,2,"局域网组网实验"));
    }
}
