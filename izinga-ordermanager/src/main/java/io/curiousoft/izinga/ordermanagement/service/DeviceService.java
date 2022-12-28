package io.curiousoft.izinga.ordermanagement.service;

import io.curiousoft.izinga.commons.model.Device;

import java.util.List;

public interface DeviceService {

    Device create(Device device) throws Exception;

    void deleteById(String deviceId);

    Device findById(String deviceId);

    Device findByToken(String token);

    List<Device> findByUserId(String userId);

    Device update(String id, Device device) throws Exception;
}
