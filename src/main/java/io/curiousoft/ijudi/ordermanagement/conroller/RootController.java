package io.curiousoft.ijudi.ordermanagement.conroller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class RootController {

    @GetMapping
    public ResponseEntity<Map<String, String>> health() {
        Map<String,String> map = new HashMap();
        map.put("status", "ok");
        return ResponseEntity.ok(map);
    }
}
