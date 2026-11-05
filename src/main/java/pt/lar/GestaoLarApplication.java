package pt.lar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GestaoLarApplication {
    public static void main(String[] args) {
        SpringApplication.run(GestaoLarApplication.class, args);
    }
}
