package com.vextis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulith;

@Modulith(systemName = "Vextis Enterprise Core")
@SpringBootApplication
public class VextisApplication {

    public static void main(String[] args) {
        SpringApplication.run(VextisApplication.class, args);
    }
}
