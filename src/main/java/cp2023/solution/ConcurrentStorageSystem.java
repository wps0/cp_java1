package cp2023.solution;

import cp2023.base.ComponentId;
import cp2023.base.ComponentTransfer;
import cp2023.base.DeviceId;
import cp2023.base.StorageSystem;
import cp2023.exceptions.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Semaphore;

public class ConcurrentStorageSystem implements StorageSystem {
    private final Semaphore devicesLock;
    private final Set<ComponentId> activeComponents;
    public ConcurrentMap<DeviceId, Device> devices; // TODO: public -> private i np. concurrent hash set?

    public ConcurrentStorageSystem() {
        this.devicesLock = new Semaphore(1, true);
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
            throw new RuntimeException("panic: unexpected thread interruption");
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
            dst.modifyFreeSpace(-1);
            devicesLock.release();
            buildExecutionChainAndExecute(p);
        } else {
            List<PendingTransfer> cycle = findCycle(p);
            if (cycle.isEmpty()) {
                if (!tryToLinkWithExecutingTransfer(p)) {
                    p.destination().insertInbound(p);
                }
                devicesLock.release();
            } else {
                removeFromGraph(cycle);
                devicesLock.release();
                linkTransfersInChain(cycle, true);
                freeAllWaiting(cycle);
            }

            executeTransfer(p);
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
            if (!tryToLinkWithExecutingTransfer(pt)) {
                dst.insertInbound(pt);
            }
            devicesLock.release();
            executeTransfer(pt);
        }
    }

    private void handleDeleteTransfer(ComponentTransfer transfer, Device src) throws InterruptedException {
        buildExecutionChainAndExecute(new PendingTransfer(transfer, src, null));
    }


    /**
     * Requires devicesLock to be held!
     */
    private PendingTransfer buildExecutionChain(PendingTransfer start) {
        List<PendingTransfer> chain = makeAllowedChain(start, start.source());
        PendingTransfer lastInChain = chain.get(chain.size()-1);
        removeFromGraph(chain);
        addExecutingTransfer(lastInChain);
        linkTransfersInChain(chain, false);

        return lastInChain;
    }

    private void buildExecutionChainAndExecute(PendingTransfer start) throws InterruptedException {
        devicesLock.acquire();
        PendingTransfer lastInChain = buildExecutionChain(start);
        devicesLock.release();

        start.prepareLock().release();
        executeTransfer(start);

        devicesLock.acquire();
        if (lastInChain.next() == null) {
            removeExecutingTransferAndFreeSpace(lastInChain);
        }
        devicesLock.release();
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

    /**
     * Requires devicesLock to be held!
     */
    private void link(PendingTransfer next, PendingTransfer previos) {
        next.setPrevious(previos);
        previos.setNext(next);
    }

    private void linkTransfersInChain(List<PendingTransfer> transfers, boolean isCycle) {
        Iterator<PendingTransfer> nextIt = transfers.iterator();
        Iterator<PendingTransfer> it = transfers.iterator();
        nextIt.next();

        while (nextIt.hasNext()) {
            PendingTransfer next = nextIt.next();
            PendingTransfer itTransfer = it.next();
            itTransfer.setNext(next);
            next.setPrevious(itTransfer);
        }

        if (isCycle) {
            PendingTransfer first = transfers.get(0);
            PendingTransfer last = transfers.get(transfers.size() - 1);
            link(first, last);
        }
    }

    /**
     * Requires devicesLock to be held!
     */
    private boolean tryToLinkWithExecutingTransfer(PendingTransfer t) {
        ConcurrentSkipListSet<PendingTransfer> et = t.destination().executingTransfers();
        if (et.isEmpty()) {
            return false;
        }

        PendingTransfer lastInChain = et.pollFirst();
        link(t, lastInChain);
        buildExecutionChain(t);
        return true;
    }

    private void removeFromGraph(Collection<PendingTransfer> transfers) {
        for (PendingTransfer t : transfers)
            if (t.destination() != null)
                t.destination().removeInbound(t);
    }

    /** Finds a cycle if it exists. Requires devicesLock to be held.
     * @return A list containing vertices which constitute the cycle if it exists, an empty list otherwise.
     */
    private List<PendingTransfer> findCycle(PendingTransfer v) {
        Stack<PendingTransfer> cycle = new Stack<>();
        if (!cycleDfs(v, cycle, v.destination()))
            return List.of();
        return cycle.stream().toList();
    }

    /**
     * Requires devicesLock to be held!
     */
    private boolean cycleDfs(PendingTransfer v, Stack<PendingTransfer> hist, Device end) {
        hist.push(v);
        if (v.source() == end)
            return true;
        if (v.source() != null) {
            for (PendingTransfer x : v.source().inbound()) {
                if (cycleDfs(x, hist, end))
                    return true;
            }
        }

        hist.pop();
        return false;
    }


    private void freeAllWaiting(List<PendingTransfer> transfers) {
        for (PendingTransfer t : transfers) {
            t.prepareLock().release();
        }
    }

    private void executeTransfer(PendingTransfer t) throws InterruptedException {
        // STARE: TODO: co gdy ostatni transfer puścił free, a po tym dołączył się inny na jego koniec -> deadlock
        if (t.previous() == null || t.previous().phrase().equals(PendingTransfer.Phrase.WAITING))
            t.prepareLock().acquire();
        t.prepare();
        if (t.previous() != null && (t.previous().phrase().equals(PendingTransfer.Phrase.WAITING) || t.previous().phrase().equals(PendingTransfer.Phrase.PREPARE)))
            t.performLock().acquire();
        t.perform();

        // update the location of components
        if (t.source() != null)
            t.source().removeComponent(t.getComponentId());
        if (t.destination() != null)
            t.destination().insertComponent(t.getComponentId());
    }

    private void addExecutingTransfer(PendingTransfer t) {
        if (t.source() != null) {
            t.source().executingTransfers().add(t);
        }
    }

    private void removeExecutingTransferAndFreeSpace(PendingTransfer t) {
        if (t.source() != null) {
            t.source().executingTransfers().remove(t);
            t.source().modifyFreeSpace(1);
        }
    }

    public void addComponent(DeviceId deviceId, ComponentId componentId) {
        Device dev = devices.get(deviceId);
        dev.insertComponent(componentId);
        dev.modifyFreeSpace(-1);
    }

    public void initialiseDevices(Map<DeviceId, Integer> deviceTotalSlots) {
        deviceTotalSlots.forEach((id, capacity) -> devices.put(id, new Device(id, capacity)));
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
}
