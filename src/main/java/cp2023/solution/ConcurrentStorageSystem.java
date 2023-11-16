package cp2023.solution;

import cp2023.base.ComponentId;
import cp2023.base.ComponentTransfer;
import cp2023.base.DeviceId;
import cp2023.base.StorageSystem;
import cp2023.exceptions.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

public class ConcurrentStorageSystem implements StorageSystem {
    private Semaphore devicesLock;
    public ConcurrentMap<DeviceId, Device> devices; // TODO: public -> private i np. concurrent hash set?
    private Set<ComponentId> activeComponents;

    public ConcurrentStorageSystem() {
        this.devicesLock = new Semaphore(1);
        this.devices = new ConcurrentHashMap<>();
        this.activeComponents = Collections.synchronizedSet(new HashSet<>());
    }

    @Override
    public void execute(ComponentTransfer transfer) throws TransferException {
        try {
            try {
                devicesLock.acquire();
                validateOrThrow(transfer);
                activeComponents.add(transfer.getComponentId());
            } finally {
                devicesLock.release();
            }

            transfer(transfer);

            activeComponents.remove(transfer.getComponentId());
        } catch (InterruptedException e) {
            handleUnexpectedInterruptedException();
        }
    }

    private void transfer(ComponentTransfer transfer) throws InterruptedException {
        Device src = transfer.getSourceDeviceId() == null ? null : devices.get(transfer.getSourceDeviceId());
        Device dst = transfer.getDestinationDeviceId() == null ? null : devices.get(transfer.getDestinationDeviceId());

        if (src != null && dst != null)
            handleMoveTransfer(transfer, src, dst);
        else if (src == null && dst != null)
            handleAddTransfer(transfer, dst);
        else if (src != null && dst == null)
            handleDeleteTransfer(transfer, src);
        else
            assert false;
    }

    private void handleMoveTransfer(ComponentTransfer transfer, Device src, Device dst) throws InterruptedException {
        PendingTransfer p = new PendingTransfer(transfer, src, dst);

        devicesLock.acquire();
        if (dst.freeSpace() > 0) {
            // doesn't wait
            moveWithoutWaiting(p, src, dst);
        } else {
            List<PendingTransfer> cycle = findCycle(p);
            if (cycle.isEmpty()) {
                p.destination().insert(p);
                devicesLock.release();

                // The thread will be released when the transfer can be executed.
                p.callingThreadLock().acquire();
                if (p.isFirst()) {
                    // waits and is first (component deleted or transferred out)
                    devicesLock.acquire();
                    moveWithoutWaiting(p, src, dst);
                } else {
                    // waits and isn't first (potentially in a cycle)
                    executeTransfer(p, true, false);
                }
            } else {
                // doesn't wait and is first
                removeFromGraph(cycle);
                devicesLock.release();
                linkTransfersInChain(cycle, true);
                freeAllWaiting(cycle);
                executeTransfer(p, false, false);
            }
        }
    }

    private void handleAddTransfer(ComponentTransfer transfer, Device dst) throws InterruptedException {
        devicesLock.acquire();

        if (dst.freeSpace() > 0) {
            dst.modifyFreeSpace(-1);
            devicesLock.release();

            transfer.prepare();
            transfer.perform();
            dst.insertComponent(transfer.getComponentId());
        } else {
            PendingTransfer pt = new PendingTransfer(transfer, null, dst);
            dst.inbound().add(pt);
            devicesLock.release();
            executeTransfer(pt, false, false);
        }
    }

    private void handleDeleteTransfer(ComponentTransfer transfer, Device src) throws InterruptedException {
        PendingTransfer pendingTransfer = new PendingTransfer(transfer, src, null);

        devicesLock.acquire();
        List<PendingTransfer> chain = makeAllowedChain(pendingTransfer, src);
        removeFromGraph(chain);
        devicesLock.release();

        linkTransfersInChain(chain, false);
        freeAllWaiting(chain);
        executeTransfer(pendingTransfer, true, true);
        freeSpaceFromLastInChainOrWake(chain);
    }

    private List<PendingTransfer> makeAllowedChain(PendingTransfer v, Device dev) {
        List<PendingTransfer> transfers = new ArrayList<>();
        Set<DeviceId> vis = new HashSet<>();

        transfers.add(v);

        while (dev != null && !dev.inbound().isEmpty()) {
            vis.add(dev.id());

            for (PendingTransfer t : dev.inbound()) {
                if (t.getSourceDeviceId() == null || !vis.contains(t.getSourceDeviceId())) {
                    transfers.add(t);
                    if (t.getSourceDeviceId() != null)
                        dev = devices.get(t.getSourceDeviceId());
                    else
                        dev = null;
                    break;
                }
            }
        }

        return transfers;
    }

    private void removeFromGraph(Collection<PendingTransfer> transfers) {
        for (PendingTransfer t : transfers)
            if (t.destination() != null)
                t.destination().remove(t);
    }


    /** Finds a cycle, if it exists.
     * @return A list containing vertices which constitute the cycle if it exists, an empty list otherwise.
     */
    private List<PendingTransfer> findCycle(PendingTransfer v) {
        Deque<PendingTransfer> cycle = new ArrayDeque<>();
        if (!cycleDfs(v, cycle, v.destination()))
            return List.of();
        return cycle.parallelStream().toList();
    }

    private boolean cycleDfs(PendingTransfer v, Deque<PendingTransfer> hist, Device end) {
        hist.push(v);
        if (v.source() == end)
            return true;
        if (v.source() != null) {
            for (PendingTransfer x : v.source().inbound()) {
                if (cycleDfs(x, hist, end))
                    return true;
            }
        }

        hist.pollLast();
        return false;
    }

    // Inherits the critical section - deviceLock.
    private void moveWithoutWaiting(PendingTransfer p, Device src, Device dst) throws InterruptedException {
        dst.modifyFreeSpace(-1);
        List<PendingTransfer> chain = makeAllowedChain(p, src);
        removeFromGraph(chain);
        devicesLock.release();

        linkTransfersInChain(chain, false);
        freeAllWaiting(chain);
        executeTransfer(p, false, true);
        freeSpaceFromLastInChainOrWake(chain);
    }

    private void linkTransfersInChain(List<PendingTransfer> transfers, boolean isCycle) {
        transfers.get(0).setFirst(true);

        Iterator<PendingTransfer> nextIt = transfers.iterator();
        Iterator<PendingTransfer> it = transfers.iterator();
        nextIt.next();

        while (nextIt.hasNext()) {
            PendingTransfer next = nextIt.next();
            it.next().setNext(next);
        }

        if (isCycle) {
            transfers.get(transfers.size() - 1).setNext(transfers.get(0));
        }
    }

    private void freeAllWaiting(List<PendingTransfer> transfers) {
        for (PendingTransfer t : transfers) {
            t.callingThreadLock().release();
        }
    }

    private void freeSpaceFromLastInChainOrWake(List<PendingTransfer> chain) throws InterruptedException {
        PendingTransfer lastInChain = chain.get(chain.size() - 1);
        Device src = null;
        if (lastInChain.getSourceDeviceId() != null)
            src = devices.get(lastInChain.getSourceDeviceId());

        if (src != null) {
            devicesLock.acquire();
            if (src.inbound().isEmpty())
                src.modifyFreeSpace(1);
            else
                wakeInboundTransfer(src);
            devicesLock.release();
        }
    }

    /**
     * Has to be called with devicesLock held!
     */
    private void wakeInboundTransfer(Device dev) {
        PendingTransfer next = dev.findOldestInbound();
        next.setFirst(true);
        next.callingThreadLock().release();
    }

    private void executeTransfer(PendingTransfer t, boolean skipFirstLock, boolean skipSecondLock) throws InterruptedException {
        if (!skipFirstLock)
            t.callingThreadLock().acquire();
        t.prepare();
        if (!skipSecondLock)
            t.callingThreadLock().acquire();
        t.perform();

        // update the location of components
        // TODO czy to potrzebne
        if (t.source() != null)
            t.source().removeComponent(t.getComponentId());
        if (t.destination() != null)
            t.destination().insertComponent(t.getComponentId());

//        // TODO ???
//        if (t.previous() == null) {
//            devicesLock.acquire();
//            t.source().modifyFreeSpace(1);
//            devicesLock.release();
//        }
    }


    /**
     * Has to be called with devicesLock held.
     */
    private void validateOrThrow(ComponentTransfer transfer) throws TransferException {
        ComponentId id = transfer.getComponentId();
        if (transfer.getSourceDeviceId() == null && transfer.getDestinationDeviceId() == null) {
            throw new IllegalTransferType(id);
        }

        if (transfer.getDestinationDeviceId() != null) {
            DeviceId did = transfer.getDestinationDeviceId();
            if (!devices.containsKey(did))
                throw new DeviceDoesNotExist(did);

            Device destination = devices.get(did);
            if (transfer.getSourceDeviceId() == null && destination.contains(id))
                throw new ComponentAlreadyExists(id, did);

            if (destination.contains(id))
                throw new ComponentDoesNotNeedTransfer(id, did);
        }

        if (transfer.getSourceDeviceId() != null) {
            DeviceId sid = transfer.getSourceDeviceId();
            if (!devices.containsKey(sid))
                throw new DeviceDoesNotExist(sid);

            Device source = devices.get(sid);
            if (!source.contains(id))
                throw new ComponentDoesNotExist(id, sid);
        }

        if (activeComponents.contains(id)) {
            throw new ComponentIsBeingOperatedOn(id);
        }
    }

    public static void handleUnexpectedInterruptedException() {
        throw new RuntimeException("panic: unexpected thread interruption");
    }

    public void initialiseDevices(Map<DeviceId, Integer> deviceTotalSlots) {
        deviceTotalSlots.forEach((id, capacity) -> devices.put(id, new Device(id, capacity)));
    }

    public void addComponent(DeviceId deviceId, ComponentId componentId) {
        Device dev = devices.get(deviceId);
        dev.insertComponent(componentId);
        dev.modifyFreeSpace(-1);
    }
}
