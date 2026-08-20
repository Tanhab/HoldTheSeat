package com.tanhab.holdtheseat.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.tanhab.holdtheseat.payment.gateway.GatewayProperties;

@SpringBootApplication
@EnableConfigurationProperties(GatewayProperties.class)
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }

}
