package eu.uniche.iguide.workingcopy.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Reads a live Resource row {@code FOR UPDATE} ({@code 06} section 17), so an apply and a concurrent save of the
 * same Resource cannot interleave between the version check and the write.
 */
@Component
public class LiveRowLock {

    private final EntityManager entityManager;

    public LiveRowLock(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public <T> Optional<T> lock(Class<T> entityType, UUID projectId, UUID id) {
        return entityManager
                .createQuery("select e from " + entityType.getSimpleName()
                        + " e where e.projectId = :projectId and e.id = :id", entityType)
                .setParameter("projectId", projectId)
                .setParameter("id", id)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultStream()
                .findFirst();
    }
}
