package sn.ucak.tpapirest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TpApiRestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TpApiRestApplication.class, args);
    }
}
