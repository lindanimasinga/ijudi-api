package io.curiousoft.izinga.ordermanagement.conroller;

import io.curiousoft.izinga.commons.model.Profile;
import io.curiousoft.izinga.commons.model.ProfileRoles;
import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.ordermanagement.service.UserProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

import static io.curiousoft.izinga.commons.utils.IjudiUtilsKt.isSAMobileNumber;

@RestController
@RequestMapping("/user")
public class UserController {


    private final UserProfileService profileService;

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
        Profile user =  isSAMobileNumber(id) ? profileService.findUserByPhone(id) : profileService.find(id);
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