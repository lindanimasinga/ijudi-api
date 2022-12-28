package io.curiousoft.izinga.ordermanagement.conroller;

import io.curiousoft.izinga.commons.model.Device;
import io.curiousoft.izinga.ordermanagement.service.DeviceService;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/device")
public class DeviceController {


    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @RequestMapping(method = RequestMethod.POST, consumes = "application/json", produces = "application/json")
    public ResponseEntity<Device> create(@Valid @RequestBody Device device) throws Exception {
        return ResponseEntity.ok(deviceService.create(device));
    }

    @PatchMapping(value = "/{id}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Device> update(@PathVariable String id, @Valid @RequestBody Device device) throws Exception {
        return ResponseEntity.ok(deviceService.update(id, device));
    }

    @GetMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity<Device> findDevice(@PathVariable String id) {
        Device device = deviceService.findById(id);
        return device != null ? ResponseEntity.ok(device) : ResponseEntity.notFound().build();
    }

    @GetMapping(produces = "application/json")
    public ResponseEntity<Device> findADevice(@RequestParam(required = false) String token,
                                              @RequestParam(required = false) String id) {
        Device device = !StringUtils.isEmpty(token) ? deviceService.findByToken(token) :
                !StringUtils.isEmpty(id) ? deviceService.findById(id) : null;
        return device != null ? ResponseEntity.ok(device) : ResponseEntity.notFound().build();
    }

    @DeleteMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity deleteUser(@PathVariable String id) {
        deviceService.deleteById(id);
        return ResponseEntity.ok().build();
    }

}