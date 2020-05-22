package io.curiousoft.ijudi.ordermanagent.conroller;

import io.curiousoft.ijudi.ordermanagent.model.Profile;
import io.curiousoft.ijudi.ordermanagent.model.UserProfile;
import io.curiousoft.ijudi.ordermanagent.service.UserProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/user")
public class UserController {


    private UserProfileService profileService;

    public UserController(UserProfileService profileService) {
        this.profileService = profileService;
    }

    @RequestMapping(method = RequestMethod.POST, consumes = "application/json", produces = "application/json")
    public ResponseEntity<Profile> create(@Valid @RequestBody UserProfile profile) throws Exception {
        return ResponseEntity.ok(profileService.create(profile));
    }

    @PatchMapping(value = "/{id}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Profile> update(@PathVariable String id, @Valid @RequestBody UserProfile profile) throws Exception {
        return ResponseEntity.ok(profileService.update(id, profile));
    }

    @GetMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity<Profile> findUser(@PathVariable String id) {
        Profile user = profileService.find(id);
        return user != null ? ResponseEntity.ok(user) : ResponseEntity.notFound().build();
    }
}