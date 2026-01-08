package com.example.ems.config;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class EmployeeConfig {

    @Bean
    UserDetailsManager userDetailsManager(DataSource dataSource){
        JdbcUserDetailsManager jdbcUserDetailsManager=new JdbcUserDetailsManager(dataSource);
        jdbcUserDetailsManager.setUsersByUsernameQuery("select userid,pwd,active from members where userid=?");
        jdbcUserDetailsManager.setAuthoritiesByUsernameQuery("select userid,role from roles where userid=?");
        return jdbcUserDetailsManager;
    }

    @Bean
    SecurityFilterChain employeeFilterChain(HttpSecurity httpSecurity) throws Exception{
        httpSecurity.authorizeHttpRequests(configurer->configurer.requestMatchers(HttpMethod.GET,"/employees").hasRole("EMPLOYEE")
        .requestMatchers(HttpMethod.GET,"/showFormForAdd").hasRole("MANAGER")
        .requestMatchers(HttpMethod.GET,"/showFormForUpdate").hasRole("MANAGER")
        .requestMatchers(HttpMethod.GET,"/save").hasRole("MANAGER")
        .requestMatchers(HttpMethod.GET,"/delete").hasRole("ADMIN"));

        //Custom login form
        httpSecurity.formLogin(Customizer.withDefaults());

        httpSecurity.formLogin(form -> form
			.loginPage("/login").defaultSuccessUrl("/employees")
			.permitAll());

        //logout
        httpSecurity.logout((logout) -> logout.logoutUrl("/logout").logoutSuccessUrl("/login"));

        //use basic authentication
        httpSecurity.httpBasic(Customizer.withDefaults());

        httpSecurity.csrf(csrf->csrf.disable());

        return httpSecurity.build();
    }

}
