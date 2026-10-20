package eu.uniche.iguide.asset.application;

import eu.uniche.iguide.asset.domain.AssetResourceType;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A Resource kind that binds Assets, implemented in that kind's package so the asset package does not
 * depend on it.
 */
public interface AssetBindingTarget {

    AssetResourceType kind();

    /** The status of the Resource in this Project, empty when it does not exist here. */
    Optional<ResourceStatus> status(UUID projectId, UUID resourceId);

    /** The given Resources of this Project, titled in the default language or by slug. */
    List<ResourceReference> references(UUID projectId, Collection<UUID> resourceIds);
}
