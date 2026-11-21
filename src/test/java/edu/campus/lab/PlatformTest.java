package edu.campus.lab;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:checks;MODE=MySQL;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${TEST_DB_USER:sa}","spring.datasource.password=${TEST_DB_PASSWORD:}",
    "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
@org.springframework.test.context.ContextConfiguration(initializers=PlatformTest.TestDatabaseGuard.class)
class PlatformTest {
    public static class TestDatabaseGuard implements org.springframework.context.ApplicationContextInitializer<org.springframework.context.ConfigurableApplicationContext>{
        public void initialize(org.springframework.context.ConfigurableApplicationContext context){
            var url=context.getEnvironment().getRequiredProperty("spring.datasource.url");
            if(!url.startsWith("jdbc:h2:mem:") && !url.matches("jdbc:mysql://[\\w.:-]+/labflow_test(?:\\?.*)?"))
                throw new IllegalArgumentException("集成测试仅允许 H2 内存库或名为 labflow_test 的专用 MySQL 测试库");
        }
    }
    @Autowired Schedule service;
    @Autowired SlotRepo slots;
    @Autowired BookingRepo bookings;
    @Autowired MockMvc mvc;
    final LocalDate day=LocalDate.now().plusDays(7);
    Lab room(){return service.addRoom(new RoomInput("测试"+java.util.UUID.randomUUID().toString().substring(0,8),"明理楼",32,1,8));}
    Booking apply(Lab r,int from,int to,String who){return service.apply(who,new RequestInput(r.id,day,from,to,"传感器采集实验"));}

    @Test void concurrentApprovalsAreAllOrNothing() throws Exception {
        Lab r=room(); Booking a=apply(r,3,4,"student1"), b=apply(r,4,5,"student2");
        var pool=Executors.newFixedThreadPool(2);var gate=new CyclicBarrier(2);
        try {
            var futures=pool.invokeAll(List.<Callable<String>>of(
                ()->race(a.id,"teacher1",gate),()->race(b.id,"teacher2",gate)),12,TimeUnit.SECONDS);
            var answers=List.of(futures.get(0).get(),futures.get(1).get());
            assertThat(answers).containsExactlyInAnyOrder("OK","第 4 节已被占用");
            assertThat(slots.findByLabIdAndDayBetween(r.id,day,day)).hasSize(2);
            assertThat(List.of(bookings.findById(a.id).orElseThrow().state,bookings.findById(b.id).orElseThrow().state))
                .containsExactlyInAnyOrder("APPROVED","PENDING");
        } finally {pool.shutdownNow();}
    }
    String race(long id,String teacher,CyclicBarrier gate)throws Exception {
        gate.await(5,TimeUnit.SECONDS);
        try{service.review(id,teacher,new ReviewInput(true,"同意"));return "OK";}
        catch(Rule ex){return ex.getMessage();}
    }
    @Test void maintenanceConflictsWithApprovalAndReleases(){
        Lab r=room();Booking b=apply(r,2,3,"student1");
        service.maintain(new BlockInput(r.id,day,3,4,"仪器校准"));
        assertThatThrownBy(()->service.review(b.id,"teacher1",new ReviewInput(true,""))).hasMessageContaining("第 3 节");
        assertThatThrownBy(()->apply(r,3,3,"student2")).hasMessageContaining("第 3 节");
        service.release(new BlockInput(r.id,day,3,4,""));
        service.review(b.id,"teacher1",new ReviewInput(true,"同意"));
        assertThatThrownBy(()->service.maintain(new BlockInput(r.id,day,1,2,"检修"))).hasMessageContaining("第 2 节");
        assertThat(slots.findByLabIdAndDayBetween(r.id,day,day)).hasSize(2);
    }
    @Test void ownerCanCancelAndReleaseApprovedSlots(){
        Lab r=room();Booking b=apply(r,2,3,"student1");
        assertThatThrownBy(()->service.cancel(b.id,"student2")).isInstanceOf(Rule.class);
        service.review(b.id,"teacher1",new ReviewInput(true,""));
        service.cancel(b.id,"student1");
        assertThat(slots.findByLabIdAndDayBetween(r.id,day,day)).isEmpty();
        assertThat(bookings.findById(b.id).orElseThrow().state).isEqualTo("CANCELLED");
    }
    @Test void rejectionKeepsOpinionAndCanNotBeApprovedAgain(){
        Lab r=room();Booking b=apply(r,1,1,"student2");
        assertThatThrownBy(()->service.review(b.id,"teacher1",new ReviewInput(false," "))).hasMessageContaining("驳回原因");
        service.review(b.id,"teacher1",new ReviewInput(false,"请补充方案"));
        assertThat(bookings.findById(b.id).orElseThrow().opinion).isEqualTo("请补充方案");
        assertThatThrownBy(()->service.review(b.id,"teacher2",new ReviewInput(true,""))).hasMessageContaining("待审");
    }
    @Test void openingWindowProtectsCurrentSchedule(){
        Lab r=room();Booking b=apply(r,1,2,"student1");service.review(b.id,"teacher1",new ReviewInput(true,""));
        assertThatThrownBy(()->service.editRoom(r.id,new RoomInput(r.name,"北楼",30,3,8))).hasMessageContaining("已有排期");
        assertThatThrownBy(()->service.apply("student1",new RequestInput(r.id,day,5,4,"实验"))).hasMessageContaining("节次");
        assertThatThrownBy(()->service.apply("student1",new RequestInput(r.id,LocalDate.now().minusDays(1),2,3,"实验"))).hasMessageContaining("日期");
    }
    @Test void statisticsCountSlotsAndExcludeMaintenance(){
        Lab r=room();Booking b=apply(r,1,2,"student1");service.review(b.id,"teacher1",new ReviewInput(true,""));
        service.maintain(new BlockInput(r.id,day,3,3,"校准"));
        RoomStat stat=service.stats(day).stream().filter(s->s.id()==r.id).findFirst().orElseThrow();
        assertThat(stat.reserved()).isEqualTo(2);assertThat(stat.capacity()).isEqualTo(55);
        assertThat(stat.peak()).containsExactly(1,2);
    }
    @Test void roleAndCsrfAreEnforced()throws Exception{
        mvc.perform(get("/api/bookings")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/stats").param("start",day.toString()).with(user("s").roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/student/bookings").with(user("s").roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/teacher/bookings/1/review").with(csrf()).with(user("s").roles("STUDENT"))).andExpect(status().isForbidden());
    }
    @Test void databaseRejectsDuplicateSlotOutsideService(){
        Lab r=room();service.maintain(new BlockInput(r.id,day,1,1,"校准"));
        Slot copy=new Slot();copy.labId=r.id;copy.day=day;copy.period=1;copy.label="重复占用";
        assertThatThrownBy(()->slots.saveAndFlush(copy)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(slots.findByLabIdAndDayBetween(r.id,day,day)).hasSize(1);
    }
    @Test void coldStartServesSeededPublicCalendar()throws Exception{
        mvc.perform(get("/api/rooms")).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("智能感知实验室"))
            .andExpect(jsonPath("$[2].name").value("嵌入式系统实验室"));
        var mon=LocalDate.now().with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.MONDAY));
        mvc.perform(get("/api/calendar").param("room","1").param("start",mon.toString())).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(8));
    }
    @Test void reviewFailureRollsBackAllFlushedInserts(){
        Lab r=room();Booking b=apply(r,2,4,"student2");
        assertThatThrownBy(()->service.review(b.id,"teacher2",new ReviewInput(true,"字".repeat(161)))).hasMessageContaining("审批意见");
        assertThat(slots.findByLabIdAndDayBetween(r.id,day,day)).isEmpty();
        assertThat(bookings.findById(b.id).orElseThrow().state).isEqualTo("PENDING");
    }
}
