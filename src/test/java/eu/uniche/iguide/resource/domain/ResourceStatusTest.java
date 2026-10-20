package eu.uniche.iguide.resource.domain;

import static eu.uniche.iguide.resource.domain.ResourceStatus.ACTIVE;
import static eu.uniche.iguide.resource.domain.ResourceStatus.ARCHIVED;
import static eu.uniche.iguide.resource.domain.ResourceStatus.DRAFT;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The transition table of {@code 03} section 1. */
class ResourceStatusTest {

    @Test
    void draftActivatesOrArchives() {
        assertThat(DRAFT.canTransitionTo(ACTIVE)).isTrue();
        assertThat(DRAFT.canTransitionTo(ARCHIVED)).isTrue();
        assertThat(DRAFT.canTransitionTo(DRAFT)).isFalse();
    }

    @Test
    void activeDeactivatesOrArchives() {
        assertThat(ACTIVE.canTransitionTo(DRAFT)).isTrue();
        assertThat(ACTIVE.canTransitionTo(ARCHIVED)).isTrue();
        assertThat(ACTIVE.canTransitionTo(ACTIVE)).isFalse();
    }

    @Test
    void archivedOnlyRestoresToDraft() {
        assertThat(ARCHIVED.canTransitionTo(DRAFT)).isTrue();
        assertThat(ARCHIVED.canTransitionTo(ACTIVE)).isFalse();
        assertThat(ARCHIVED.canTransitionTo(ARCHIVED)).isFalse();
        assertThat(ARCHIVED.canTransitionTo(null)).isFalse();
    }

    @Test
    void deactivationAndArchivalLeaveVisitors() {
        assertThat(ACTIVE.leavesVisitorsFor(DRAFT)).isTrue();
        assertThat(ACTIVE.leavesVisitorsFor(ARCHIVED)).isTrue();
        assertThat(DRAFT.leavesVisitorsFor(ARCHIVED)).isTrue();
        assertThat(DRAFT.leavesVisitorsFor(ACTIVE)).isFalse();
        assertThat(ARCHIVED.leavesVisitorsFor(DRAFT)).isFalse();
    }
}
