package org.teamsai.saibackend.global.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class GuestPageController {

    @GetMapping({"/", "/intro"})
    public String introPage() {
        return "guest/intro";
    }
}
