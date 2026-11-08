package com.wsb.animaladoption.controller;

import com.wsb.animaladoption.security.AltchaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Issues new ALTCHA challenges - the widget on the registration form fetches one from here (JSON). */
@RestController
@RequiredArgsConstructor
public class AltchaController {
    private final AltchaService altchaService;

    @GetMapping("/altcha/challenge")
    public ResponseEntity<AltchaService.Challenge> challenge() {
        // Every challenge is single-use, so the browser must not cache it
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(altchaService.createChallenge());
    }
}
