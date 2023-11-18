package cp2023.solution;

import cp2023.base.ComponentId;
import cp2023.base.ComponentTransfer;
import cp2023.base.DeviceId;

import java.util.concurrent.Semaphore;

public class PendingTransfer implements ComponentTransfer, Comparable<PendingTransfer> {
    private final ComponentTransfer originalTransfer;
    private final Device source;
    private final Device destination;
    private final Semaphore prepareLock;
    private final Semaphore performLock;
    private PendingTransfer next;
    private PendingTransfer previous;
    private volatile Phrase phrase;

    public PendingTransfer(ComponentTransfer originalTransfer, Device source, Device destination) {
        this.originalTransfer = originalTransfer;
        this.source = source;
        this.destination = destination;
        this.performLock = new Semaphore(0);
        this.prepareLock = new Semaphore(0);
        this.phrase = Phrase.WAITING;
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
        phrase = Phrase.PREPARE;
        if (next != null)
            next.prepareLock.release();
        originalTransfer.prepare();
        if (next != null)
            next.performLock.release();
    }

    @Override
    public void perform() {
        phrase = Phrase.PERFORM;
        originalTransfer.perform();
        phrase = Phrase.FINISHED;
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

    @Override
    public String toString() {
        return "PendingTransfer{" +
                "originalTransfer=" + originalTransfer +
                ", source=" + (source == null ? "null" : source.id()) +
                ", destination=" + (destination == null ? "null" : destination.id()) +
                ", next=" + (next != null ? next.originalTransfer.toString() : "null") +
                '}';
    }

    public Phrase phrase() {
        return phrase;
    }

    public PendingTransfer previous() {
        return previous;
    }

    public void setPrevious(PendingTransfer previous) {
        this.previous = previous;
    }

    public Semaphore prepareLock() {
        return prepareLock;
    }

    public Semaphore performLock() {
        return performLock;
    }

    @Override
    public int compareTo(PendingTransfer pendingTransfer) {
        return originalTransfer.getComponentId().compareTo(pendingTransfer.getComponentId());
    }

    public enum Phrase {
        WAITING, PREPARE, PERFORM, FINISHED
    }
}
