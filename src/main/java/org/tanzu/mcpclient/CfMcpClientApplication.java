package org.tanzu.mcpclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CfMcpClientApplication {

	public static void main(String[] args) {
		SpringApplication.run(CfMcpClientApplication.class, args);
	}


    private static final Logger logger = LoggerFactory.getLogger(CfMcpClientApplication.class);

}
