package tn.iteam.orderservice.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import tn.iteam.orderservice.config.FeignConfig;
import tn.iteam.orderservice.dto.UserDto;

@FeignClient(
        name = "auth-register-service",
        url = "http://localhost:8085/api/auth",
        configuration = FeignConfig.class
)
public interface AuthClient {

    @GetMapping("/me")
    UserDto getCurrentUser();
}

