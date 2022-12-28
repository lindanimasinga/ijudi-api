package io.curiousoft.izinga.ordermanagement.service;

import io.curiousoft.izinga.commons.model.Device;
import io.curiousoft.izinga.commons.repo.DeviceRepository;
import org.springframework.stereotype.Service;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class DeviceServiceImpl implements DeviceService {

    private final DeviceRepository deviceRepo;
    private final Validator validator;

    public DeviceServiceImpl(DeviceRepository deviceRepo) {
        this.deviceRepo = deviceRepo;
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Override
    public Device create(Device device) throws Exception {
        validate(device);
        Optional<Device> deviceOptional = deviceRepo.findOneByToken(device.getToken());
        return deviceOptional.orElseGet(() -> deviceRepo.save(device));
    }

    @Override
    public void deleteById(String deviceId) {
        deviceRepo.deleteById(deviceId);
    }

    @Override
    public Device findById(String deviceId) {
        return deviceRepo.findById(deviceId).orElse(null);
    }

    @Override
    public Device findByToken(String token) {
        return deviceRepo.findOneByToken(token).orElse(null);
    }

    @Override
    public List<Device> findByUserId(String userId) {
        return deviceRepo.findByUserId(userId);
    }

    @Override
    public Device update(String id, Device device) throws Exception {
        validate(device);
        Device oldDevice = deviceRepo.findOneByIdOrToken(id, device.getToken()).orElse(null);
        if(oldDevice == null) {
            return deviceRepo.save(device);
        }
        oldDevice.setUserId(device.getUserId());
        return deviceRepo.save(oldDevice);
    }

    protected void validate(Object profile) throws Exception {
        Set<ConstraintViolation<Object>> violations = validator.validate(profile);
        if(violations.size() > 0) {
            throw new Exception(violations.iterator().next().getMessage());
        }
    }
}
