package io.curiousoft.ijudi.ordermanagent.conroller;

import io.curiousoft.ijudi.ordermanagent.model.Profile;
import io.curiousoft.ijudi.ordermanagent.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/user")
public class UserController {


    private UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<Profile> create(@RequestBody @Valid Profile profile) throws Exception {
        return ResponseEntity.ok(userService.create(profile));
    }

    @PatchMapping(value = "/{id}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Profile> update(@PathVariable String id, @RequestBody @Valid Profile profile) throws Exception {
        return ResponseEntity.ok(userService.update(id, profile));
    }

    @GetMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity<Profile> findUser(@RequestParam String id) {
        Profile user = userService.find(id);
        return user != null ? ResponseEntity.ok(user) : ResponseEntity.notFound().build();
    }
}