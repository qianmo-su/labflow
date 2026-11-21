package edu.campus.lab;

import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class Access {
    static final Map<String,String> NAMES=Map.of("student1","王思齐","student2","李嘉宁","teacher1","陈老师","teacher2","徐老师","admin","周管理员");
    @Bean UserDetailsService users(){
        var manager=new InMemoryUserDetailsManager();var hash=new BCryptPasswordEncoder().encode("LabDemo123");
        NAMES.forEach((id,name)->manager.createUser(User.withUsername(id).password("{bcrypt}"+hash)
            .roles(id.startsWith("student")?"STUDENT":id.startsWith("teacher")?"TEACHER":"ADMIN").build()));
        return manager;
    }
    @Bean SecurityFilterChain rules(HttpSecurity http)throws Exception{
        return http.authorizeHttpRequests(a->a
            .requestMatchers("/","/index.html","/assets/**","/api/session","/api/rooms","/api/calendar","/error").permitAll()
            .requestMatchers("/api/student/**").hasRole("STUDENT").requestMatchers("/api/teacher/**").hasRole("TEACHER")
            .requestMatchers("/api/admin/**").hasRole("ADMIN").anyRequest().authenticated())
            .requestCache(c->c.disable())
            .formLogin(f->f.loginProcessingUrl("/login").successHandler((r,s,a)->s.setStatus(204))
                .failureHandler((r,s,e)->s.setStatus(401)).permitAll())
            .logout(l->l.logoutUrl("/logout").logoutSuccessHandler((r,s,a)->s.setStatus(204)))
            .exceptionHandling(e->e.authenticationEntryPoint((r,s,ex)->s.setStatus(401)))
            .build();
    }
}
