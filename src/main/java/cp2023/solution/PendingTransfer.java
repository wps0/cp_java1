package cp2023.solution;

import cp2023.base.ComponentId;
import cp2023.base.ComponentTransfer;
import cp2023.base.DeviceId;

import java.util.Optional;

public class PendingTransfer implements ComponentTransfer {
    private final ComponentTransfer originalTransfer;
    private Device source;
    private Device destination;
    private Optional<PendingTransfer> next;
    // The transfer which will occupy the space taken by this component.
    private Optional<PendingTransfer> previous;


    public PendingTransfer(ComponentTransfer originalTransfer, Device source, Device destination) {
        this.originalTransfer = originalTransfer;
        this.source = source;
        this.destination = destination;
        this.next = Optional.empty();
        this.previous = Optional.empty();
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
    }

    @Override
    public void perform() {
        originalTransfer.perform();
    }

    public PendingTransfer nextInCycle() {
        return nextInCycle;
    }

    public void setNextInCycle(PendingTransfer nextInCycle) {
        this.nextInCycle = nextInCycle;
    }
}
