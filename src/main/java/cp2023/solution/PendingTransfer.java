package cp2023.solution;

import cp2023.base.ComponentId;
import cp2023.base.ComponentTransfer;
import cp2023.base.DeviceId;

import java.util.concurrent.Semaphore;

public class PendingTransfer implements ComponentTransfer {
    private final ComponentTransfer originalTransfer;
    private final Device source;
    private final Device destination;
    private PendingTransfer next;
    // The transfer which will occupy the space taken by this component.
    private PendingTransfer previous;
    private final Semaphore previousWaitingSemaphore;
    private final Semaphore callingThreadLock;

    public PendingTransfer(ComponentTransfer originalTransfer, Device source, Device destination) {
        this.originalTransfer = originalTransfer;
        this.source = source;
        this.destination = destination;
        this.previousWaitingSemaphore = new Semaphore(0);
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
        callingThreadLock.acquire();
        originalTransfer.prepare();
        previousWaitingSemaphore.release();
    }

    @Override
    public void perform() {
        try {
            if (next != null) {
                next.previousWaitingSemaphore.acquire();
            }
            originalTransfer.perform();
        } catch (InterruptedException e) {
            ConcurrentStorageSystem.handleUnexpectedInterruptedException();
        }

        if (previous == null && source != null) {
            source.alterFreeSpace(-1);
        }
    }

    public void connect(PendingTransfer preceding) {
        preceding.next = this;
        previous = preceding;
    }

    public Semaphore callingThreadLock() {
        return callingThreadLock;
    }
}
