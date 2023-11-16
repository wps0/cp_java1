/*
 * University of Warsaw
 * Concurrent Programming Course 2023/2024
 * Java Assignment
 *
 * Author: Konrad Iwanicki (iwanicki@mimuw.edu.pl)
 */
package cp2023.solution;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import cp2023.base.ComponentId;
import cp2023.base.DeviceId;
import cp2023.base.StorageSystem;


public final class StorageSystemFactory {

    public static StorageSystem newSystem(
            Map<DeviceId, Integer> deviceTotalSlots,
            Map<ComponentId, DeviceId> componentPlacement) {
        // https://moodle.mimuw.edu.pl/mod/forum/discuss.php?d=9261
        if (deviceTotalSlots.isEmpty()) {
            throw new IllegalArgumentException("Cannot initialise a system without devices");
        }

        // https://moodle.mimuw.edu.pl/mod/forum/discuss.php?d=9191
        Integer minCapacity = deviceTotalSlots.values().stream()
                .min(Integer::compareTo)
                .get();
        if (minCapacity <= 0) {
            throw new IllegalArgumentException("A device cannot have capacity <= 0");
        }

//        // TODO: powtarzające się id?
//        Set<DeviceId> deviceIds = new HashSet<>();
//        for (DeviceId id : deviceTotalSlots.keySet()) {
//            System.out.println("Device id " + id);
//            if (deviceIds.contains(id)) {
//                throw new IllegalArgumentException("Multiple devices with the same id");
//            }
//            deviceIds.add(id);
//        }

        // Device overflow & non-existent devices
        Map<DeviceId, Long> count = componentPlacement.entrySet()
                .parallelStream()
                .map(Map.Entry::getValue)
                .collect(Collectors.groupingBy(deviceId -> deviceId, Collectors.counting()));

        for (Map.Entry<DeviceId, Long> assignment : count.entrySet()) {
            if (!deviceTotalSlots.containsKey(assignment.getKey())) {
                throw new IllegalArgumentException("Component assigned to a non-existent device");
            }

            if (assignment.getValue() > deviceTotalSlots.get(assignment.getKey())) {
                throw new IllegalArgumentException("Device overflow");
            }
        }

        ConcurrentStorageSystem sys = new ConcurrentStorageSystem();
        sys.initialiseDevices(deviceTotalSlots);
        componentPlacement.forEach((componentId, deviceId) -> sys.addComponent(deviceId, componentId));
        return sys;
    }
}
