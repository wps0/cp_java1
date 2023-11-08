package cp2023.solution;

import cp2023.base.ComponentId;
import cp2023.base.ComponentTransfer;
import cp2023.base.DeviceId;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Device {
    private final DeviceId id;
    private final int capacity;
    private int freeSpace;
    private ConcurrentMap<ComponentId, Component> storage;

    public Device(DeviceId id, int capacity) {
        this.id = id;
        this.capacity = capacity;
        this.storage = new ConcurrentHashMap<>();
        this.freeSpace = capacity;
    }

    public Optional<PendingTransfer> queueTransferIfAllowed(PendingTransfer transfer) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    public boolean contains(ComponentId id) {
        return storage.containsKey(id);
    }

    private boolean isTransferAllowed(ComponentTransfer transfer) {
        if (freeSpace > 0)
            return true;
        throw new UnsupportedOperationException("not yet implemented");
    }

    public DeviceId id() {
        return id;
    }
}
