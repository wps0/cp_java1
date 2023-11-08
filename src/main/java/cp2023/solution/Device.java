package cp2023.solution;

import cp2023.base.ComponentId;
import cp2023.base.ComponentTransfer;
import cp2023.base.DeviceId;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

public class Device {
    private final DeviceId id;
    private final int capacity;
    private final Semaphore waitingForTransfer;
    private int freeSpace;
    private ConcurrentMap<ComponentId, Component> storage;

    public Device(DeviceId id, int capacity) {
        this.id = id;
        this.capacity = capacity;
        this.storage = new ConcurrentHashMap<>();
        this.freeSpace = capacity;
        this.waitingForTransfer = new Semaphore(capacity);
    }

    public Optional<PendingTransfer> queueTransferIfAllowed(PendingTransfer transfer) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    public boolean contains(ComponentId id) {
        return storage.containsKey(id);
    }

    public void alterFreeSpace(int delta) {
        // freeSpace += delta;
        throw new UnsupportedOperationException("not yet implemented");
    }

    private boolean isTransferAllowed(ComponentTransfer transfer) {
        if (freeSpace > 0)
            return true;
        throw new UnsupportedOperationException("not yet implemented");
    }

    public DeviceId id() {
        return id;
    }

    public Semaphore waitingForTransfer() {
        return waitingForTransfer;
    }
}
