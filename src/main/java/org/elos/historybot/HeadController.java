package org.elos.historybot;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController

public class HeadController {

    @RequestMapping(method = RequestMethod.HEAD)
    public ResponseEntity<?> response() {
        return ResponseEntity.ok().build();
    }
}
