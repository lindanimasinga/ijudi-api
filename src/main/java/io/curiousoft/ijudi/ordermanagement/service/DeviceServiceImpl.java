package io.curiousoft.ijudi.ordermanagement.service;

import io.curiousoft.ijudi.ordermanagement.model.Device;
import io.curiousoft.ijudi.ordermanagement.repo.DeviceRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.List;
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
        return deviceRepo.save(device);
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
        return deviceRepo.findByToken(token).orElse(null);
    }

    @Override
    public List<Device> findByUserId(String userId) {
        return deviceRepo.findByUserId(userId);
    }

    @Override
    public Device update(String id, Device device) throws Exception {
        validate(device);
        Device oldDevice = deviceRepo.findById(id).orElse(null);
        if(oldDevice == null) {
            return null;
        }
        BeanUtils.copyProperties(device, oldDevice);
        return deviceRepo.save(oldDevice);
    }

    protected void validate(Object profile) throws Exception {
        Set<ConstraintViolation<Object>> violations = validator.validate(profile);
        if(violations.size() > 0) {
            throw new Exception(violations.iterator().next().getMessage());
        }
    }
}
