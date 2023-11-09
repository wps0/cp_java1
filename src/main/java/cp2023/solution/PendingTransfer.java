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
    private PendingTransfer previous;

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
        if (previous != null)
            previous.callingThreadLock.release();
    }

    @Override
    public void perform() {
        originalTransfer.perform();
    }

    public Semaphore callingThreadLock() {
        return callingThreadLock;
    }

    public PendingTransfer previous() {
        return previous;
    }

    public void setPrevious(PendingTransfer previous) {
        this.previous = previous;
    }

    public Device source() {
        return source;
    }

    public Device destination() {
        return destination;
    }
}
