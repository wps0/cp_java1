package cp2023.solution;

import java.util.concurrent.Semaphore;

public record TransferData(Semaphore prepareSemaphore, Semaphore performSemaphore) {
}
