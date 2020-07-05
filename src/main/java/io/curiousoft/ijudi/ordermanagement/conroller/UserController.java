package io.curiousoft.ijudi.ordermanagement.conroller;

import io.curiousoft.ijudi.ordermanagement.model.Profile;
import io.curiousoft.ijudi.ordermanagement.model.ProfileRoles;
import io.curiousoft.ijudi.ordermanagement.model.UserProfile;
import io.curiousoft.ijudi.ordermanagement.service.UserProfileService;
import io.curiousoft.ijudi.ordermanagement.utils.IjudiUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

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
        Profile user =  IjudiUtils.isSAMobileNumber(id) ? profileService.findUserByPhone(id) : profileService.find(id);
        return user != null ? ResponseEntity.ok(user) : ResponseEntity.notFound().build();
    }

    @GetMapping(produces = "application/json")
    public ResponseEntity<List<UserProfile>> findUsers(@RequestParam(required = false) ProfileRoles role,
                                                   @RequestParam(required = false) double latitude,
                                                   @RequestParam(required = false) double longitude,
                                                   @RequestParam(required = false) double range) {
        List<UserProfile> users = role != null? profileService.findByLocation(role, latitude, longitude, range)
                : profileService.findAll();
        return ResponseEntity.ok(users);
    }

    @DeleteMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity deleteUser(@PathVariable String id) {
        profileService.delete(id);
        return ResponseEntity.ok().build();
    }

}