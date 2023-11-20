package cp2023.solution;

import cp2023.base.ComponentId;
import cp2023.base.DeviceId;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;

public class Device {
    private final DeviceId id;
    private final int capacity;
    private int freeSpace;
    private final ConcurrentMap<ComponentId, Boolean> components;
    private final Queue<PendingTransfer> inbound;
    private final ConcurrentSkipListSet<PendingTransfer> executingTransfers;

    public Device(DeviceId id, int capacity) {
        this.id = id;
        this.capacity = capacity;
        this.components = new ConcurrentHashMap<>();
        this.inbound = new LinkedList<>();
        this.executingTransfers = new ConcurrentSkipListSet<>();
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

    public Queue<PendingTransfer> inbound() {
        return inbound;
    }

    public ConcurrentSkipListSet<PendingTransfer> executingTransfers() {
        return executingTransfers;
    }

    void insertInbound(PendingTransfer t) {
        inbound.add(t);
    }

    void removeInbound(PendingTransfer t) {
        inbound.remove(t);
    }

    void insertComponent(ComponentId id) {
        components.put(id, true);
    }

    void removeComponent(ComponentId id) {
        components.remove(id);
    }

    public int freeSpace() {
        return freeSpace;
    }

    @Override
    public String toString() {
        return "Device{" +
                "id=" + id +
                ", capacity=" + capacity +
                ", freeSpace=" + freeSpace +
                ", components=" + components +
                ", inbound=" + inbound +
                '}';
    }
}