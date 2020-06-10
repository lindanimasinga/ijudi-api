package io.curiousoft.ijudi.ordermanagement.conroller;

import io.curiousoft.ijudi.ordermanagement.model.Profile;
import io.curiousoft.ijudi.ordermanagement.model.UserProfile;
import io.curiousoft.ijudi.ordermanagement.service.UserProfileService;
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

    @GetMapping(produces = "application/json")
    public ResponseEntity<Profile> findUserByPhone(@RequestParam String phone) {
        Profile user = profileService.findOrderByPhone(phone);
        return user != null ? ResponseEntity.ok(user) : ResponseEntity.notFound().build();
    }

    @DeleteMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity deleteUser(@PathVariable String id) {
        profileService.delete(id);
        return ResponseEntity.ok().build();
    }

}