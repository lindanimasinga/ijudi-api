package io.curiousoft.izinga.ordermanagement.service;

import io.curiousoft.izinga.commons.model.Device;
import io.curiousoft.izinga.commons.repo.DeviceRepository;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class DeviceServiceImplTest {

    //sut
    private DeviceServiceImpl sut;
    @Mock
    private DeviceRepository deviceRepo;

    @Before
    public void setUp() {
        sut = new DeviceServiceImpl(deviceRepo);
    }

    @Test
    public void add() throws Exception {

        //given
        String pushToken = "23423werwlekrjwlekjr23423j4l2k3j423gdfgergerg";
        Device device = new Device(pushToken);

        //when
        when(deviceRepo.save(device)).thenReturn(device);

        Device newDevice = sut.create(device);

        //verify
        Assert.assertNotNull(newDevice);
        verify(deviceRepo).save(device);
    }

    @Test
    public void addAlreadyExist() throws Exception {

        //given
        String pushToken = "23423werwlekrjwlekjr23423j4l2k3j423gdfgergerg";
        Device device = new Device(pushToken);

        Device oldDevice = new Device(pushToken);
        oldDevice.setId("111");

        //when
        when(deviceRepo.findOneByToken("23423werwlekrjwlekjr23423j4l2k3j423gdfgergerg")).thenReturn(Optional.of(oldDevice));

        Device newDevice = sut.create(device);

        //verify
        Assert.assertEquals(oldDevice, newDevice);
        verify(deviceRepo, times(0)).save(oldDevice);
    }

    @Test
    public void createDeviceAlreadyExist() throws Exception {

        //given
        String pushToken = "23423werwlekrjwlekjr23423j4l2k3j423gdfgergerg";
        Device device = new Device(pushToken);
        //when
        when(deviceRepo.findOneByToken("23423werwlekrjwlekjr23423j4l2k3j423gdfgergerg"))
                .thenReturn(Optional.of(device));
        Device newDevice = sut.create(device);

        //verify
        verify(deviceRepo, times(0)).save(device);
        verify(deviceRepo).findOneByToken("23423werwlekrjwlekjr23423j4l2k3j423gdfgergerg");
    }

    @Test
    public void addNoToken() {
        //given
        String pushToken = "";
        Device device = new Device(pushToken);

        //when
        try {
            Device newDevice = sut.create(device);
            fail();
        } catch (Exception e) {
            Assert.assertEquals("device token required", e.getMessage());
        }

        //verify
        verify(deviceRepo, never()).save(device);
    }

    @Test
    public void update() throws Exception {

        //given
        String pushToken = "23423werwlekrjwlekjr23423j4l2k3j423gdfgergerg";
        Device oldDevice = new Device(pushToken);
        oldDevice.setUserId("old user");
        Device updateDevice = new Device("23423werwlekrjwlekjr23423j4l2k3j423gdfgergerg");
        updateDevice.setUserId("new userId");

        //when
        when(deviceRepo.findOneByIdOrToken("deviceId", updateDevice.getToken())).thenReturn(Optional.of(oldDevice));
        when(deviceRepo.save(oldDevice)).thenReturn(oldDevice);
        Device newDevice = sut.update("deviceId", updateDevice);

        //verify
        Assert.assertNotNull(newDevice);
        Assert.assertEquals(newDevice.getId(), oldDevice.getId());
        Assert.assertEquals(newDevice.getToken(), updateDevice.getToken());
        Assert.assertEquals(newDevice.getUserId(), updateDevice.getUserId());
        verify(deviceRepo).save(oldDevice);
    }

    @Test
    public void updateNoToken() {
        //given
        String pushToken = "";
        Device device = new Device(pushToken);

        //when
        try {
            Device newDevice = sut.update("id", device);
            fail();
        } catch (Exception e) {
            Assert.assertEquals("device token required", e.getMessage());
        }

        //verify
        verify(deviceRepo, never()).save(device);
    }

    @Test
    public void delete() {

        //when
        sut.deleteById("id");

        //verify
        verify(deviceRepo).deleteById("id");
    }

    @Test
    public void findById() throws Exception {

        Device device = new Device("khlkashdkjagsfuoiwerbkjbsfds");
        //when

        when(deviceRepo.findById("id")).thenReturn(java.util.Optional.of(device));
        sut.findById("id");

        //verify
        Assert.assertNotNull(device);
        verify(deviceRepo).findById("id");
    }


    @Test
    public void findByToken() throws Exception {
        String token = "khlkashdkjagsfuoiwerbkjbsfds";
        Device device = new Device(token);
        //when

        when(deviceRepo.findOneByToken(token)).thenReturn(Optional.of(device));
        sut.findByToken(token);

        //verify
        Assert.assertNotNull(device);
        verify(deviceRepo).findOneByToken(token);
    }

    @Test
    public void findByUserId() {

        String token = "khlkashdkjagsfuoiwerbkjbsfds";
        String userId = "userId";
        Device device = new Device(token);
        //when

        when(deviceRepo.findByUserId(userId)).thenReturn(Collections.singletonList(device));
        List<Device> devices = sut.findByUserId(userId);

        //verify
        Assert.assertEquals(1, devices.size());
        verify(deviceRepo).findByUserId(userId);
    }
}