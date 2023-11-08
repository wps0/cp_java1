package cp2023.solution;

import cp2023.base.ComponentTransfer;
import cp2023.base.DeviceId;
import cp2023.base.StorageSystem;
import cp2023.exceptions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

public class ConcurrentStorageSystem implements StorageSystem {
    private Map<DeviceId, Device> devices;
    private Semaphore commitSemaphore;

    public ConcurrentStorageSystem() {
        this.devices = new HashMap<>();
        this.commitSemaphore = new Semaphore(1, true);
    }

    @Override
    public void execute(ComponentTransfer transfer) throws TransferException {
        try {
            TransferData status = commit(transfer);

            status.prepareSemaphore().acquire();
            transfer.prepare();
            status.performSemaphore().acquire();
            transfer.perform();
            // ...
        } catch (InterruptedException e) {
            handleUnexpectedInterruptedException();
        }
    }

    private TransferData commit(ComponentTransfer transfer) throws TransferException, InterruptedException {
        commitSemaphore.acquire();
        try {
            validateOrThrow(transfer);
            // The transfer will be executed - it becomes a pending transfer

        } finally {
            commitSemaphore.release();
        }
    }

    private void validateOrThrow(ComponentTransfer transfer) throws TransferException {
        if (transfer.getSourceDeviceId() == null && transfer.getDestinationDeviceId() == null) {
            throw new IllegalTransferType(transfer.getComponentId());
        }

        if (!devices.containsKey(transfer.getDestinationDeviceId())) {
            throw new DeviceDoesNotExist(transfer.getDestinationDeviceId());
        }

        if (!devices.containsKey(transfer.getSourceDeviceId())) {
            throw new DeviceDoesNotExist(transfer.getSourceDeviceId());
        }

        Device source = devices.get(transfer.getSourceDeviceId());
        Device destination = devices.get(transfer.getDestinationDeviceId());
        if (!source.contains(transfer.getComponentId())) {
            throw new ComponentDoesNotExist(transfer.getComponentId(), source.id());
        }

        if (destination.contains(transfer.getComponentId())) {
            throw new ComponentDoesNotNeedTransfer(transfer.getComponentId(), source.id());
        }

        // TODO: komponent, którego dotyczy transfer, jest jeszcze transferowany (wyjątek ComponentIsBeingOperatedOn).
    }

    public static void handleUnexpectedInterruptedException() {
        throw new RuntimeException("panic: unexpected thread interruption");
    }
}
