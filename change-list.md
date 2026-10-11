# Player-visible changes

## Since 0.8.3

- **Mob Bucket ↔ vanilla mob buckets:** Using a Mob Bucket while holding a water-based vanilla or modded mob bucket (Bucket of Cod, Bucket of Axolotl, etc.) in the other hand moves that mob into the Mob Bucket and leaves a Water Bucket. Holding a Water Bucket instead moves the Mob Bucket's oldest mob out into its own mob bucket, if that mob has one. Only what the vanilla bucket format records is kept, and the moved mob gets a new UUID. Filling a mob bucket this way counts toward bucket advancements such as Tactical Fishing.
- **Junk and Trash Bucket tempting:** Holding a Junk or Trash Bucket in either hand tempts mobs that would follow a player holding any of its stored items.
- **Mob Bucket "No room to release":** When a sneak-release fails because the mob would collide with a block, the action bar now says "No room to release" instead of nothing happening. This matters most for mobs wider than a block, such as Alex's Mobs frilled sharks.
