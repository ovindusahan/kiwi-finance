package nz.kiwifinance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class KiwiFinanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(KiwiFinanceApplication.class, args);
    }
}
