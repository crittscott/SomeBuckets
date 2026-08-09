package com.github.crittscott.somebuckets;

import net.fabricmc.api.ModInitializer;

/**
 * Fabric entry point. Registers nothing yet: every one of the mod's six item classes currently
 * lives in the Forge module, not {@code common}, because they all depend on {@code util.NBTUtil}
 * for their basic persistent state (mode, stored items, entity snapshots) and {@code NBTUtil}
 * itself is Forge-{@code FluidStack}-typed throughout — it is not just the fluid-content items
 * (Big/Huge/Source Bucket) that are blocked from moving to common, but Junk, Trash, and Mob Bucket
 * too, even though their own behavior has no fluid-capability coupling at all.
 *
 * <p>TODO(fabric): the real prerequisite for any item registration here is redesigning
 * {@code NBTUtil} so its loader-neutral responsibilities (mode, stored-item list, entity snapshots)
 * are separated from its Forge-{@code FluidStack}-typed ones (fluid amount/identity), giving
 * {@code common} a fluid-amount representation with no Forge type in it. That redesign is real,
 * separate design work — seeded here as a decision to revisit, not solved by this pass. See
 * multi-loader-transition.md, "Explicitly deferred".
 *
 * <p>TODO(fabric): once {@code NBTUtil} is common-safe, start with Junk/Trash/Mob Bucket (their own
 * logic already has zero Forge imports beyond that dependency) before attempting Big/Huge/Source
 * Bucket, which additionally need a Fabric Transfer API ({@code Storage<FluidVariant>})-based
 * fluid-handling implementation with no Forge equivalent to port.
 */
public final class SomeBucketsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // Intentionally empty — see class Javadoc.
    }
}
