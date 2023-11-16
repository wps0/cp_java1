package cp2023.solution;

import cp2023.base.ComponentId;
import cp2023.base.DeviceId;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StorageSystemFactoryTest {

    @Test
    void shouldThrowExceptionOnDeviceOverflowTest() {
        // given
        Map<DeviceId, Integer> devices = new HashMap<>();
        Map<ComponentId, DeviceId> components = new HashMap<>();

        DeviceId d1 = new DeviceId(101);
        DeviceId d2 = new DeviceId(102);
        devices.put(d1, 1);
        devices.put(d2, 1);

        ComponentId c1 = new ComponentId(1);
        ComponentId c2 = new ComponentId(2);
        ComponentId c3 = new ComponentId(3);
        components.put(c1, d2);
        components.put(c2, d1);
        components.put(c3, d2);

        // then
        assertThrows(IllegalArgumentException.class, () -> StorageSystemFactory.newSystem(devices, components));
    }

    @Test
    void shouldReturnAProperSystemTest() {
        // given
        Map<DeviceId, Integer> devices = new HashMap<>();
        Map<ComponentId, DeviceId> components = new HashMap<>();

        DeviceId d1 = new DeviceId(101);
        DeviceId d2 = new DeviceId(102);
        devices.put(d1, 1);
        devices.put(d2, 2);

        ComponentId c1 = new ComponentId(1);
        ComponentId c2 = new ComponentId(2);
        ComponentId c3 = new ComponentId(3);
        components.put(c1, d2);
        components.put(c2, d1);
        components.put(c3, d2);

        // then
        assertDoesNotThrow(() -> StorageSystemFactory.newSystem(devices, components));
    }

    @Test
    void shouldThrowExceptionOnNonexistentDeviceTest() {
        // given
        Map<DeviceId, Integer> devices = new HashMap<>();
        Map<ComponentId, DeviceId> components = new HashMap<>();

        DeviceId d1 = new DeviceId(101);
        DeviceId d2 = new DeviceId(102);
        devices.put(d1, 1);

        ComponentId c1 = new ComponentId(1);
        ComponentId c2 = new ComponentId(2);
        ComponentId c3 = new ComponentId(3);
        components.put(c2, d1);
        components.put(c1, d2);

        // then
        assertThrows(IllegalArgumentException.class, () -> StorageSystemFactory.newSystem(devices, components));
    }
}