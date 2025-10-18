package com.tanggo.fund.bitcoin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
        "com.tanggo.fund.bitcoin",
        "com.tanggo.fund.eth"
})
public class BitcoinJDddApplication {

    public static void main(String[] args) {
        SpringApplication.run(BitcoinJDddApplication.class, args);
    }

}
