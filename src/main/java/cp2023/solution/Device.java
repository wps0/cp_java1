package cp2023.solution;

import cp2023.base.ComponentId;
import cp2023.base.DeviceId;

import java.awt.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

public class Device {
    private final DeviceId id;
    private final int capacity;
    private int freeSpace;
    private ConcurrentMap<ComponentId, Boolean> components;
    private ConcurrentLinkedQueue<PendingTransfer> inbound;

    public Device(DeviceId id, int capacity) {
        this.id = id;
        this.capacity = capacity;
        this.components = new ConcurrentHashMap<>();
        this.inbound = new ConcurrentLinkedQueue<>();
        this.freeSpace = capacity;
    }

    public void modifyFreeSpace(int delta) {
        freeSpace += delta;
    }

    public boolean contains(ComponentId id) {
        return components.containsKey(id);
    }

    public DeviceId id() {
        return id;
    }

    public ConcurrentMap<ComponentId, Boolean> components() {
        return components;
    }

    public ConcurrentLinkedQueue<PendingTransfer> inbound() {
        return inbound;
    }

    public PendingTransfer findOldestInbound() {
        return inbound.peek();
    }

    public int freeSpace() {
        return freeSpace;
    }
}