package com.github.crittscott.somebuckets.protection;

/** Categories of protected operations performed by Some Buckets. */
public enum ProtectionAction {
    /** Create, remove, or waterlog fluid at an exact world position. */
    FLUID_EDIT,
    /** Place, remove, or replace a non-fluid block. */
    BLOCK_EDIT,
    /** Transfer fluid through or otherwise operate on a block capability or cauldron. */
    BLOCK_INTERACT,
    /** Capture, feed, collect, or otherwise interact with a target entity. */
    ENTITY_INTERACT,
    /** Spawn a stored mob or eject a stored item entity into the world. */
    ENTITY_RELEASE
}
