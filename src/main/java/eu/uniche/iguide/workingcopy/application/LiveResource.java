package eu.uniche.iguide.workingcopy.application;

import eu.uniche.iguide.resource.domain.ResourceStatus;

/** The lifecycle state of a live row, read under lock. */
public record LiveResource(ResourceStatus status, long version) {
}
