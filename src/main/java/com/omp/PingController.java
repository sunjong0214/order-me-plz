package com.omp;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {

    // 부하 테스트의 기준선(네트워크 + 프레임워크 오버헤드) 측정용 no-op 엔드포인트. DB 미접근.
    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }
}
