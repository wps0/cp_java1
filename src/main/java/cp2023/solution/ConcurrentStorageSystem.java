package cp2023.solution;

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
    private ConcurrentMap<DeviceId, Device> devices;

    public ConcurrentStorageSystem() {
        this.devicesLock = new Semaphore(1);
        this.devices = new ConcurrentHashMap<>();
    }

    @Override
    public void execute(ComponentTransfer transfer) throws TransferException {
        validateOrThrow(transfer);

    }

    private void transfer(ComponentTransfer transfer) throws InterruptedException {
        Device src = devices.getOrDefault(transfer.getSourceDeviceId(), null);
        Device dst = devices.getOrDefault(transfer.getDestinationDeviceId(), null);

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
        devicesLock.acquire();

        if (dst.freeSpace() > 0) {
            dst.modifyFreeSpace(-1);


        }
    }

    private void handleAddTransfer(ComponentTransfer transfer, Device dst) throws InterruptedException {
        devicesLock.acquire();

        if (dst.freeSpace() > 0) {
            dst.modifyFreeSpace(-1);
            devicesLock.release();

            transfer.prepare();
            transfer.perform();
        } else {
            PendingTransfer p = new PendingTransfer(transfer, null, dst);
            dst.inbound().add(p);

            List<PendingTransfer> cycle = findCycle(p);
            removeFromGraph(cycle);
            devicesLock.release();
            if (!cycle.isEmpty()) {
                linkTransfersInChain(cycle, true);
                freeAllWaiting(cycle);
            }

            executeTransfer(p);
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
        executeTransfer(pendingTransfer);
        freeSpaceFromLastInChain(chain);
    }

    private List<PendingTransfer> makeAllowedChain(PendingTransfer v, Device dev) {
        List<PendingTransfer> transfers = new ArrayList<>();
        Set<DeviceId> vis = new HashSet<>();

        while (!dev.inbound().isEmpty()) {
            vis.add(dev.id());
            transfers.add(v);

            for (PendingTransfer t : dev.inbound()) {
                if (t.getSourceDeviceId() == null || !vis.contains(t.getSourceDeviceId())) {
                    v = t;
                    if (t.getSourceDeviceId() != null)
                        dev = devices.get(t.getSourceDeviceId());
                    break;
                }
            }
        }

        return transfers;
    }

    private void removeFromGraph(Collection<PendingTransfer> transfers) {
        for (PendingTransfer t : transfers)
            if (t.destination() != null)
                t.destination().inbound().remove(t);
    }

    private List<PendingTransfer> findCycle(PendingTransfer v) {
        Deque<PendingTransfer> cycle = new ArrayDeque<>();
        if (!cycleDfs(v, cycle, v.destination()))
            return List.of();
        cycle.addFirst(v);
        return cycle.parallelStream().toList();
    }

    private boolean cycleDfs(PendingTransfer v, Deque<PendingTransfer> hist, Device end) {
        for (PendingTransfer x : v.source().inbound()) {
            if (x.source() != null && x.source() != end) {
                hist.push(x);
                cycleDfs(x, hist, end);
                hist.pollLast();
            } else
                return x.source() == end;
        }
        return false;
    }

    private void linkTransfersInChain(List<PendingTransfer> transfers, boolean isCycle) {
        PendingTransfer prv = isCycle ? transfers.get(transfers.size()-1) : null;
        for (PendingTransfer t : transfers) {
            t.setPrevious(prv);
            prv = t;
        }
    }

    private void freeAllWaiting(List<PendingTransfer> transfers) {
        for (PendingTransfer t : transfers) {
            t.callingThreadLock().release();
        }
    }

    private void freeSpaceFromLastInChain(List<PendingTransfer> chain) throws InterruptedException {
        PendingTransfer lastInChain = chain.get(chain.size() - 1);
        Device src = devices.getOrDefault(lastInChain.getSourceDeviceId(), null);

        if (src != null) {
            devicesLock.acquire();
            src.modifyFreeSpace(1);
            // todo: trigger further etc
            devicesLock.release();
        }
    }

    private void executeTransfer(PendingTransfer t) throws InterruptedException {
        t.callingThreadLock().acquire();
        t.prepare();
        t.callingThreadLock().acquire();
        t.perform();

        // update the location of components
        t.source().components().remove(t.getComponentId());
        if (t.destination() != null)
            t.destination().components().put(t.getComponentId(), true);
    }

    private void validateOrThrow(ComponentTransfer transfer) throws TransferException {
//        if (transfer.getSourceDeviceId() == null && transfer.getDestinationDeviceId() == null) {
//            throw new IllegalTransferType(transfer.getComponentId());
//        }
//
//        if (!devices.containsKey(transfer.getDestinationDeviceId())) {
//            throw new DeviceDoesNotExist(transfer.getDestinationDeviceId());
//        }
//
//        if (!devices.containsKey(transfer.getSourceDeviceId())) {
//            throw new DeviceDoesNotExist(transfer.getSourceDeviceId());
//        }
//
//        Device source = devices.get(transfer.getSourceDeviceId());
//        Device destination = devices.get(transfer.getDestinationDeviceId());
//        if (!source.contains(transfer.getComponentId())) {
//            throw new ComponentDoesNotExist(transfer.getComponentId(), source.id());
//        }
//
//        if (destination.contains(transfer.getComponentId())) {
//            throw new ComponentDoesNotNeedTransfer(transfer.getComponentId(), source.id());
//        }
//
//        // TODO: komponent, którego dotyczy transfer, jest jeszcze transferowany (wyjątek ComponentIsBeingOperatedOn).
    }

    public static void handleUnexpectedInterruptedException() {
        throw new RuntimeException("panic: unexpected thread interruption");
    }
}
