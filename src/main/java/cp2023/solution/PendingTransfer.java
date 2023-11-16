package cp2023.solution;

import cp2023.base.ComponentId;
import cp2023.base.ComponentTransfer;
import cp2023.base.DeviceId;

import java.util.concurrent.Semaphore;

public class PendingTransfer implements ComponentTransfer {
    private final ComponentTransfer originalTransfer;
    private final Device source;
    private final Device destination;
    private final Semaphore callingThreadLock;
    private PendingTransfer next;
    private boolean isFirst;

    public PendingTransfer(ComponentTransfer originalTransfer, Device source, Device destination) {
        this.originalTransfer = originalTransfer;
        this.source = source;
        this.destination = destination;
        this.callingThreadLock = new Semaphore(0);
    }

    @Override
    public ComponentId getComponentId() {
        return originalTransfer.getComponentId();
    }

    @Override
    public DeviceId getSourceDeviceId() {
        return originalTransfer.getSourceDeviceId();
    }

    @Override
    public DeviceId getDestinationDeviceId() {
        return originalTransfer.getDestinationDeviceId();
    }

    @Override
    public void prepare() {
        originalTransfer.prepare();
        if (next != null)
            next.callingThreadLock.release();
    }

    @Override
    public void perform() {
        originalTransfer.perform();
    }

    public Semaphore callingThreadLock() {
        return callingThreadLock;
    }

    public PendingTransfer next() {
        return next;
    }

    public void setNext(PendingTransfer next) {
        this.next = next;
    }

    public Device source() {
        return source;
    }

    public Device destination() {
        return destination;
    }

    public void setFirst(boolean isFirst) {
        this.isFirst = isFirst;
    }

    public boolean isFirst() {
        return isFirst;
    }

    @Override
    public String toString() {
        return "PendingTransfer{" +
                "originalTransfer=" + originalTransfer +
                ", source=" + (source == null ? "null" : source.id()) +
                ", destination=" + (destination == null ? "null" : destination.id()) +
                ", next=" + (next != null ? next.originalTransfer.toString() : "null") +
                ", isFirst=" + isFirst +
                '}';
    }
}
