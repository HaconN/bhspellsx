package net.offkung.bhspellsx.entity.spells.crystal_hydro_dome;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import io.redspace.ironsspellbooks.entity.spells.AbstractConeProjectile;
import io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile;
import io.redspace.ironsspellbooks.entity.spells.AoeEntity;
import io.redspace.ironsspellbooks.registries.SoundRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.offkung.bhspellsx.registry.BHXEntityRegistry;
import net.offkung.bhspellsx.registry.BHXSpellRegistry;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Li Ming — water (main). Stationary hemisphere dome: redirects damage taken by entities inside
 * it to its own HP pool, counters outside Player attackers with a lightning strike back, blocks
 * incoming projectiles at its boundary, and heals/knocks back on open and (if it survives) close.
 * <p>
 * Unlike amethyst_decree/embracing_bosom, this dome's membership test is a single hemisphere
 * formula (isInside), not the entity's own AoeEntity-derived bounding box — AoeEntity.m_6972_
 * hardcodes height to 1.2 regardless of setRadius(), which would truncate a 5-block-tall
 * hemisphere. Every spatial query in this class builds its own AABB via searchBox()/isInside()
 * instead of relying on this.getBoundingBox().
 * <p>
 * The dome never moves after spawn (no gravity, no velocity) — its "center" is simply
 * this.position() at all times, so no separate center field is kept in sync.
 * <p>
 * CrystalHydroDomeEvents (a different package) needs to find "the dome a given victim is
 * currently inside" without relying on this entity's own (too-short) bounding box for spatial
 * lookup, so active instances are tracked in a small static registry (ACTIVE_DOMES) instead,
 * added/removed via onAddedToWorld/onRemovedFromWorld — robust to any removal path (discard(),
 * chunk unload, etc.), not just an explicit discard() call.
 */
public class CrystalHydroDomeAoe extends AoeEntity {
    // Cosmetic only. Native glow is spawned locally, at most one every three ticks per dome.
    // Keep this common-side safe: no Minecraft client classes or renderer dependencies here.
    private static final int CLIENT_GLOW_INTERVAL = 3;
    private static final int CLIENT_GLOW_RISE_TICKS = 30;
    // Public: BHSpellsX's common-setup check logs an ERROR against these ids if either mob
    // effect can't be resolved, mirroring amethyst_decree's own EFN_STOP_ID check. NOT efn:stop —
    // rejected after in-game testing: efn:stop's client-side input lock is enforced by 4 Mixins
    // (MixinKeyboardHandler/MixinMouseHandler/MixinKeyMapping) that all gate on the mere presence
    // of efn:stop specifically, blocking every keypress/click/look — including inventory, F3, and
    // the pause menu, not just movement. horizontalstop/verticalstop freeze position exactly the
    // same way (one X/Z, one Y) but aren't referenced by any of those mixins, so look/attack/menus
    // stay usable. See MERGE.md §C for the full writeup.
    public static final ResourceLocation EFN_HORIZONTALSTOP_ID = ResourceLocation.fromNamespaceAndPath("efn", "horizontalstop");
    public static final ResourceLocation EFN_VERTICALSTOP_ID = ResourceLocation.fromNamespaceAndPath("efn", "verticalstop");

    // Phase 2C counter bolt: a small ring buffer of attacker offsets (relative to the dome center)
    // plus a serial number, carried by the entity's normal synced-data packet — no custom channel,
    // nothing sent per tick, only when a Counter actually fires. Several Counters in one tick
    // (AoE on N people inside) each get their own slot, up to COUNTER_BOLT_SLOTS per tick.
    public static final int COUNTER_BOLT_SLOTS = 8;
    private static final EntityDataAccessor<Integer> DATA_COUNTER_SERIAL =
            SynchedEntityData.defineId(CrystalHydroDomeAoe.class, EntityDataSerializers.INT);
    @SuppressWarnings("unchecked")
    private static final EntityDataAccessor<Vector3f>[] DATA_COUNTER_TARGETS = new EntityDataAccessor[COUNTER_BOLT_SLOTS];
    static {
        for (int i = 0; i < COUNTER_BOLT_SLOTS; i++) {
            DATA_COUNTER_TARGETS[i] = SynchedEntityData.defineId(CrystalHydroDomeAoe.class, EntityDataSerializers.VECTOR3);
        }
    }

    // Phase 2D end animation: which ending is playing, synced once at finish().
    public static final int END_STATE_ALIVE = 0;
    public static final int END_STATE_NATURAL = 1;
    public static final int END_STATE_BROKEN = 2;
    private static final EntityDataAccessor<Integer> DATA_END_STATE =
            SynchedEntityData.defineId(CrystalHydroDomeAoe.class, EntityDataSerializers.INT);

    private static final Set<CrystalHydroDomeAoe> ACTIVE_DOMES =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    private double currentHp = CrystalHydroDomeConstants.DOME_HP;

    /** Guards finish() against re-entry: Utils.serverSideCancelCast (called on an early end)
     *  synchronously calls back into CrystalHydroDomeSpell.onServerCastComplete(cancelled=true),
     *  which itself tries to end the dome early again. Set as the very first thing finish() does,
     *  before any other side effect, so that re-entrant call sees it and no-ops immediately. */
    private boolean ended = false;

    /** Dome-owned i-frame gating for outside-sourced hits — per victim, independent of the
     *  victim's own (real) vanilla invulnerability window. See gateOutsideHit() for why the
     *  real vanilla window can't be used here: the dome always cancels outside hits, so vanilla
     *  itself never sets invulnerableTime/lastHurt for them. */
    private final Map<UUID, IFrameRecord> victimIFrames = new HashMap<>();

    private record IFrameRecord(long lastTick, double lastAmount) {
    }

    /** Action-bar HP readout (caster only). Starts true so the very first tick sends one —
     *  absorb() also sets this from outside tick() (the LivingAttackEvent path), so tick()'s own
     *  flush is what actually coalesces those into at most one send per tick. */
    private boolean hpReadoutDirty = true;

    // Client-only bookkeeping for the counter bolt (plain ints, no client classes). Start tick is
    // this entity's own client tickCount when the serial update arrived.
    private int seenCounterSerial = 0;
    private final int[] counterBoltStartTick = new int[COUNTER_BOLT_SLOTS];
    private final int[] counterBoltSerial = new int[COUNTER_BOLT_SLOTS];
    /** Client: tickCount when the end state arrived. */
    private int endStartTick = 0;
    /** Server: ticks spent lingering after finish(). */
    private int lingerTicks = 0;

    {
        java.util.Arrays.fill(counterBoltStartTick, Integer.MIN_VALUE / 2);
    }

    public CrystalHydroDomeAoe(EntityType<? extends Projectile> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
        this.setRadius((float) CrystalHydroDomeConstants.RADIUS);
    }

    public CrystalHydroDomeAoe(Level level) {
        this(BHXEntityRegistry.CRYSTAL_HYDRO_DOME_AOE.get(), level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_COUNTER_SERIAL, 0);
        this.entityData.define(DATA_END_STATE, END_STATE_ALIVE);
        for (EntityDataAccessor<Vector3f> target : DATA_COUNTER_TARGETS) {
            this.entityData.define(target, new Vector3f());
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key == DATA_END_STATE) {
            // A client that starts tracking mid-ending just plays the animation from its start.
            endStartTick = this.tickCount;
            return;
        }
        // Null guard: a set() during super-construction would run before our field initializers.
        if (key != DATA_COUNTER_SERIAL || counterBoltStartTick == null || !this.level().isClientSide()) {
            return;
        }
        int serial = this.entityData.get(DATA_COUNTER_SERIAL);
        // tickCount 0 = the spawn packet's initial data for a client that starts tracking an
        // already-live dome; adopt the serial without replaying Counters that happened earlier.
        if (this.tickCount > 0) {
            for (int s = Math.max(seenCounterSerial, serial - COUNTER_BOLT_SLOTS); s < serial; s++) {
                int slot = Math.floorMod(s, COUNTER_BOLT_SLOTS);
                counterBoltStartTick[slot] = this.tickCount;
                counterBoltSerial[slot] = s;
            }
        }
        seenCounterSerial = serial;
    }

    public int getEndState() {
        return this.entityData.get(DATA_END_STATE);
    }

    /** Client: tickCount the ending started on (only meaningful when getEndState() != ALIVE). */
    public int getEndStartTick() {
        return endStartTick;
    }

    /** Client: tick the bolt in `slot` started on (far negative if never). */
    public int getCounterBoltStartTick(int slot) {
        return counterBoltStartTick[slot];
    }

    /** Client: serial of the Counter in `slot`, used as the renderer's jag seed. */
    public int getCounterBoltSerial(int slot) {
        return counterBoltSerial[slot];
    }

    /** Attacker's body center at Counter time, relative to the dome center. Do not mutate. */
    public Vector3f getCounterBoltOffset(int slot) {
        return this.entityData.get(DATA_COUNTER_TARGETS[slot]);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** Never persisted — a crash/restart simply loses the dome; the caster's tag is stripped
     *  defensively on next login instead (see CrystalHydroDomeEvents.onPlayerLoggedIn). */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        ACTIVE_DOMES.add(this);
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        ACTIVE_DOMES.remove(this);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isRemoved()) {
            return;
        }
        if (this.level().isClientSide()) {
            if (getEndState() == END_STATE_ALIVE && this.tickCount % CLIENT_GLOW_INTERVAL == 0) {
                double angle = this.tickCount * 0.32;
                double radius = 0.7 + 0.25 * Math.sin(this.tickCount * 0.13);
                double height = 0.25 + 1.5 * (this.tickCount % CLIENT_GLOW_RISE_TICKS)
                        / CLIENT_GLOW_RISE_TICKS;
                this.level().addParticle(ParticleTypes.GLOW,
                        this.getX() + Math.cos(angle) * radius, this.getY() + height,
                        this.getZ() + Math.sin(angle) * radius,
                        -Math.sin(angle) * 0.012, 0.018, Math.cos(angle) * 0.012);
            }
            return;
        }

        // Already finished: gameplay is over (see finish()); only count down the visual linger.
        if (ended) {
            lingerTicks++;
            if (getEndState() == END_STATE_BROKEN && (lingerTicks == 3 || lingerTicks == 7)) {
                playShatterTail(lingerTicks);
            }
            if (lingerTicks >= CrystalHydroDomeConstants.END_LINGER_TICKS) {
                this.discard();
            }
            return;
        }

        Entity owner = this.getOwner();
        if (!(owner instanceof LivingEntity caster) || !caster.isAlive() || caster.level() != this.level()) {
            endEarly();
            return;
        }
        if (currentHp <= 0) {
            endEarly();
            return;
        }
        if (!isInside(caster.position())) {
            endEarly();
            return;
        }

        reapplyStop(caster);
        scanProjectiles();
        flushHpReadout(caster);

        // Fallback only — CrystalHydroDomeSpell.onServerCastComplete(cancelled=false) is the
        // primary natural-end trigger now (see that class's javadoc for why: onCast() itself
        // fires on the terminal CONTINUOUS pulse too, so relying on this tick-based check as the
        // ONLY natural-end path was the N5 double-cast bug — the dome would end itself here
        // before Iron's own terminal onCast() pulse fired, leaving hasActiveDomeFor() false and
        // letting a second, cast-less dome spawn). This only ever ENDS an already-alive dome, so
        // it can't reintroduce that bug even if it fires. FALLBACK_END_GRACE_TICKS of slack past
        // DURATION_TICKS in case cast state is somehow lost and onServerCastComplete never fires.
        if (this.tickCount >= CrystalHydroDomeConstants.DURATION_TICKS + CrystalHydroDomeConstants.FALLBACK_END_GRACE_TICKS) {
            naturalEnd();
        }
    }

    /** Unused — required abstract in AoeEntity. All damage/heal logic here is driven from tick()
     *  and CrystalHydroDomeEvents, not AoeEntity's own reapplication-delay checkHits()/applyEffect. */
    @Override
    public void applyEffect(LivingEntity target) {
    }

    /** No idle particle spawner of its own — boundary is now traced by CrystalHydroDomeRenderer's
     *  mesh, not per-tick particles. Required: abstract in AoeEntity. */
    @Override
    public float getParticleCount() {
        return 0.0f;
    }

    /** No idle particle spawner of its own — boundary is now traced by CrystalHydroDomeRenderer's
     *  mesh, not per-tick particles. Required: abstract in AoeEntity. */
    @Override
    public Optional<ParticleOptions> getParticle() {
        return Optional.empty();
    }

    // --- Membership test — the one isInside() helper used everywhere ---

    public static boolean isInside(Vec3 center, Vec3 point) {
        double dx = point.x - center.x;
        double dy = point.y - center.y;
        double dz = point.z - center.z;
        if (dy < CrystalHydroDomeConstants.BELOW_CENTER_TOLERANCE) {
            return false;
        }
        double verticalTerm = Math.max(dy, 0.0) * Math.max(dy, 0.0);
        return dx * dx + dz * dz + verticalTerm <= CrystalHydroDomeConstants.RADIUS_SQUARED;
    }

    public boolean isInside(Vec3 point) {
        return isInside(this.position(), point);
    }

    /** Search box for a hemisphere centered on `center`, inflated by `margin` in every direction. */
    public static AABB searchBox(Vec3 center, double margin) {
        double r = CrystalHydroDomeConstants.RADIUS + margin;
        double bottom = CrystalHydroDomeConstants.BELOW_CENTER_TOLERANCE - margin;
        double top = CrystalHydroDomeConstants.HEIGHT + margin;
        return new AABB(center.x - r, center.y + bottom, center.z - r,
                center.x + r, center.y + top, center.z + r);
    }

    private <T extends LivingEntity> List<T> gatherInside(Class<T> type) {
        Vec3 center = this.position();
        return this.level().getEntitiesOfClass(type, searchBox(center, 0),
                e -> !(e instanceof ArmorStand) && e.isAlive() && isInside(e.position()));
    }

    // --- Cross-package lookups for CrystalHydroDomeEvents ---

    /** The live dome (if any, in the victim's own level) that `victim` is currently inside. */
    public static CrystalHydroDomeAoe findDomeContaining(LivingEntity victim) {
        for (CrystalHydroDomeAoe dome : ACTIVE_DOMES) {
            if (dome.isRemoved() || dome.level() != victim.level()) {
                continue;
            }
            if (dome.isInside(victim.position())) {
                return dome;
            }
        }
        return null;
    }

    /** PlayerLoggedOutEvent safety net (and CrystalHydroDomeSpell's onServerCastComplete(cancelled)
     *  hook) — ends every dome this caster owns, no heal/knockback. Takes LivingEntity (not just
     *  ServerPlayer) since onServerCastComplete's entity parameter is typed that broadly, even
     *  though in practice the caster is always the ServerPlayer Apoli cast this spell on. */
    public static void endActiveDomeFor(LivingEntity caster) {
        for (CrystalHydroDomeAoe dome : List.copyOf(ACTIVE_DOMES)) {
            if (!dome.isRemoved() && dome.level() == caster.level() && dome.getOwner() == caster) {
                dome.endEarly();
            }
        }
    }

    /** CrystalHydroDomeSpell's onServerCastComplete(cancelled=false) hook — natural cast
     *  completion drives the dome's own natural end (heal + knockback) directly, rather than
     *  relying solely on the dome's own tick()-based DURATION_TICKS check (see that class's
     *  javadoc for the N5 bug this replaces). */
    public static void naturalEndFor(LivingEntity caster) {
        for (CrystalHydroDomeAoe dome : List.copyOf(ACTIVE_DOMES)) {
            if (!dome.isRemoved() && dome.level() == caster.level() && dome.getOwner() == caster) {
                dome.naturalEnd();
            }
        }
    }

    /** Subtracts up to `amount` from the dome's HP, clamped at 0. Returns what was actually taken —
     *  callers (tick()'s projectile layer and CrystalHydroDomeEvents' damage layer) use this both
     *  to cap victim/damage math and to size the Counter. */
    public double absorb(double amount) {
        double actual = Math.min(Math.max(amount, 0.0), currentHp);
        if (actual > 0) {
            currentHp -= actual;
            // Set from outside tick() too (CrystalHydroDomeEvents' LivingAttackEvent handler) —
            // flushHpReadout() (called once per tick, from tick()) is what coalesces this down to
            // at most one send per tick regardless of how many hits land in between.
            hpReadoutDirty = true;
        }
        return actual;
    }

    public double getCurrentHp() {
        return currentHp;
    }

    /** Counter: only against a Player, sized off what the dome actually absorbed, using the
     *  spell's own damage source with the CASTER as causing entity — same setIFrames(0) pattern
     *  as AmethystDecreeSpell/AmethystDecreeAoe. No range limit. */
    public void applyCounter(Entity attackerOrOwner, double domeAbsorbed) {
        if (!(attackerOrOwner instanceof Player player) || domeAbsorbed <= 0) {
            return;
        }
        Entity caster = this.getOwner();
        if (caster == null) {
            return;
        }
        double counterDamage = domeAbsorbed * CrystalHydroDomeConstants.COUNTER_RATIO;
        DamageSources.applyDamage(player, (float) counterDamage, getDamageSource(caster));
        pushCounterBolt(player);
    }

    /** Server: records the bolt target in the next ring-buffer slot and bumps the serial. Stored
     *  relative to the dome center so float precision holds at any world coordinate. */
    private void pushCounterBolt(Entity target) {
        int serial = this.entityData.get(DATA_COUNTER_SERIAL);
        Vector3f offset = new Vector3f((float) (target.getX() - this.getX()),
                (float) (target.getY() + target.getBbHeight() * 0.5 - this.getY()),
                (float) (target.getZ() - this.getZ()));
        this.entityData.set(DATA_COUNTER_TARGETS[Math.floorMod(serial, COUNTER_BOLT_SLOTS)], offset);
        this.entityData.set(DATA_COUNTER_SERIAL, serial + 1);
    }

    private DamageSource getDamageSource(Entity causingEntity) {
        return ((AbstractSpell) BHXSpellRegistry.CRYSTAL_HYDRO_DOME.get()).getDamageSource(this, causingEntity);
    }

    /**
     * Dome-owned i-frame gating for an outside-sourced hit against `victim` (CrystalHydroDomeEvents
     * always cancels these, so the dome tracks its own per-victim state instead of relying on the
     * victim's real vanilla invulnerableTime/lastHurt — see the class-level note on
     * victimIFrames for why: since the hit never reaches LivingEntity.actuallyHurt(), vanilla's own
     * invulnerableTime/lastHurt are never set for it, so a real per-tick source like a beam/ray
     * spell would otherwise drain the dome fresh every single tick).
     * <p>
     * Honors a spell's own requested i-frame length: {@link SpellDamageSource#getIFrames()}
     * defaults to -1 (no override); IS's own DamageSources.postHitEffects (a LivingHurtEvent
     * handler) applies it by overwriting the target's real invulnerableTime with it whenever
     * it's >= 0 (0 meaning "no gating at all"). Since our victim's real invulnerableTime is never
     * touched for a canceled hit, we read getIFrames() ourselves off the incoming source instead
     * and use it as our own gating window in place of the 10-tick default.
     * <p>
     * Returns how much of `amount` the dome should take (before the overall HP cap — callers
     * still pass this through absorb()).
     */
    public double gateOutsideHit(LivingEntity victim, DamageSource source, double amount) {
        if (source.is(DamageTypeTags.BYPASSES_COOLDOWN)) {
            recordIFrame(victim, amount);
            return amount;
        }
        int window = iFrameWindowTicks(source);
        if (window <= 0) {
            recordIFrame(victim, amount);
            return amount;
        }
        long now = this.level().getGameTime();
        IFrameRecord previous = victimIFrames.get(victim.getUUID());
        if (previous != null && (now - previous.lastTick()) < window) {
            if (amount <= previous.lastAmount()) {
                return 0.0;
            }
            double delta = amount - previous.lastAmount();
            victimIFrames.put(victim.getUUID(), new IFrameRecord(previous.lastTick(), amount));
            return delta;
        }
        recordIFrame(victim, amount);
        return amount;
    }

    private void recordIFrame(LivingEntity victim, double amount) {
        victimIFrames.put(victim.getUUID(), new IFrameRecord(this.level().getGameTime(), amount));
    }

    private static int iFrameWindowTicks(DamageSource source) {
        if (source instanceof SpellDamageSource spellDamageSource && spellDamageSource.getIFrames() >= 0) {
            return spellDamageSource.getIFrames();
        }
        return CrystalHydroDomeConstants.DEFAULT_IFRAME_WINDOW_TICKS;
    }

    // --- Action-bar HP readout (caster only, server-side send) ---

    /** Flushes at most one action-bar send per tick: on the dome's first tick (hpReadoutDirty
     *  starts true), every HP_READOUT_INTERVAL_TICKS ticks regardless of change (so the action
     *  bar — which fades after ~3s if not resent — stays up), and on any tick where absorb() set
     *  the dirty flag since the last flush. */
    private void flushHpReadout(LivingEntity caster) {
        boolean periodic = this.tickCount % CrystalHydroDomeConstants.HP_READOUT_INTERVAL_TICKS == 0;
        if (!hpReadoutDirty && !periodic) {
            return;
        }
        hpReadoutDirty = false;
        if (caster instanceof ServerPlayer player && isOnline(player)) {
            player.displayClientMessage(buildHpReadoutComponent(), true);
        }
    }

    private Component buildHpReadoutComponent() {
        int hp = Math.max(0, (int) Math.ceil(currentHp));
        int max = (int) Math.ceil(CrystalHydroDomeConstants.DOME_HP);
        boolean low = currentHp < CrystalHydroDomeConstants.DOME_HP * CrystalHydroDomeConstants.HP_READOUT_LOW_THRESHOLD;

        MutableComponent label = Component.translatable("ui.bhspellsx.crystal_hydro_dome_hp_label")
                .withStyle(ChatFormatting.GREEN);
        MutableComponent heart = Component.literal("❤").withStyle(ChatFormatting.RED);
        MutableComponent value = Component.translatable("ui.bhspellsx.crystal_hydro_dome_hp_value", hp, max)
                .withStyle(low ? ChatFormatting.RED : ChatFormatting.WHITE);

        return label.append(Component.literal(" ")).append(heart).append(Component.literal(" ")).append(value);
    }

    /** Sent on any end path (natural, broken, caster moved/dead, logout) so the readout
     *  disappears immediately instead of lingering for the action bar's own ~3s fade. */
    private void clearHpReadout() {
        Entity owner = this.getOwner();
        if (owner instanceof ServerPlayer player && isOnline(player)) {
            player.displayClientMessage(Component.empty(), true);
        }
    }

    /** Guards every action-bar send (readout and clear) against a caster reference that's still
     *  a live Java object (e.g. held by ACTIVE_DOMES/getOwner()'s cachedOwner) but whose network
     *  session has already ended — identity-checked against the current player list, not just a
     *  null check, so a stale reference from a previous session can't false-positive. */
    private static boolean isOnline(ServerPlayer player) {
        return player.getServer() != null
                && player.getServer().getPlayerList().getPlayer(player.getUUID()) == player;
    }

    // --- Every-tick work ---

    private void reapplyStop(LivingEntity caster) {
        MobEffect horizontal = ForgeRegistries.MOB_EFFECTS.getValue(EFN_HORIZONTALSTOP_ID);
        if (horizontal != null) {
            caster.addEffect(new MobEffectInstance(horizontal, CrystalHydroDomeConstants.STOP_REAPPLY_DURATION_TICKS, 0, false, true, true));
        }
        MobEffect vertical = ForgeRegistries.MOB_EFFECTS.getValue(EFN_VERTICALSTOP_ID);
        if (vertical != null) {
            caster.addEffect(new MobEffectInstance(vertical, CrystalHydroDomeConstants.STOP_REAPPLY_DURATION_TICKS, 0, false, true, true));
        }
    }

    // --- Projectile layer ---

    private void scanProjectiles() {
        Vec3 center = this.position();
        AABB box = searchBox(center, CrystalHydroDomeConstants.PROJECTILE_SCAN_MARGIN);
        List<Projectile> candidates = this.level().getEntitiesOfClass(Projectile.class, box, p -> true);
        for (Projectile projectile : candidates) {
            if (projectile == this || projectile instanceof AoeEntity || projectile instanceof AbstractConeProjectile
                    || projectile instanceof ThrownEnderpearl) {
                continue;
            }
            if (projectile.getTags().contains(CrystalHydroDomeConstants.REFLECTED_TAG)) {
                continue;
            }

            Vec3 pos = projectile.position();
            if (isInside(center, pos)) {
                continue; // already inside, not "incoming"
            }

            Vec3 to = pos.add(projectile.getDeltaMovement());
            if (!segmentEntersDome(center, pos, to)) {
                continue;
            }

            Entity owner = projectile.getOwner();
            if (owner != null && isInside(center, owner.position())) {
                continue; // owner inside -> ignore
            }

            handleIncomingProjectile(projectile, owner);
        }
    }

    private boolean segmentEntersDome(Vec3 center, Vec3 from, Vec3 to) {
        double length = from.distanceTo(to);
        if (length < 1.0e-6) {
            return isInside(center, to);
        }
        int steps = Math.max(1, (int) Math.ceil(length / CrystalHydroDomeConstants.SEGMENT_SAMPLE_STEP));
        for (int i = 1; i <= steps; i++) {
            Vec3 sample = from.lerp(to, (double) i / steps);
            if (isInside(center, sample)) {
                return true;
            }
        }
        return false;
    }

    private void handleIncomingProjectile(Projectile projectile, Entity owner) {
        double rawAmount;
        if (projectile instanceof AbstractArrow arrow) {
            rawAmount = predictArrowDamage(arrow);
            arrow.setDeltaMovement(arrow.getDeltaMovement().scale(-1.0));
            arrow.addTag(CrystalHydroDomeConstants.REFLECTED_TAG);
            // Original owner is deliberately left untouched (spec: "keep the ORIGINAL owner") —
            // Projectile.leftOwner is already permanently true for an in-flight arrow (set once
            // by checkLeftOwner() the first tick it clears the owner's hitbox), so the reversed
            // arrow can hit its original shooter with no extra handling.
        } else if (projectile instanceof AbstractMagicProjectile magicProjectile) {
            rawAmount = magicProjectile.getDamage();
            projectile.discard();
        } else {
            rawAmount = 0.0;
            projectile.discard();
        }
        double absorbed = absorb(rawAmount);
        applyCounter(owner, absorbed);
    }

    /** Vanilla AbstractArrow.onHitEntity's own formula (velocity.length() * baseDamage, ceil'd),
     *  plus — if isCritArrow() — the midpoint of vanilla's crit bonus range
     *  (random.nextInt(i/2+2), i.e. uniformly in [0, i/2+1]) since the actual roll can't be
     *  reproduced without replicating the entity's RNG state pre-hit. */
    private double predictArrowDamage(AbstractArrow arrow) {
        double velocityLength = arrow.getDeltaMovement().length();
        double base = Math.ceil(Mth.clamp(velocityLength * arrow.getBaseDamage(), 0.0, (double) Integer.MAX_VALUE));
        if (arrow.isCritArrow()) {
            int iBase = (int) base;
            double midpointBonus = (iBase / 2 + 1) / 2.0;
            base += midpointBonus;
        }
        return base;
    }

    // --- End of life ---

    private void endEarly() {
        finish(false);
    }

    private void naturalEnd() {
        finish(true);
    }

    private void finish(boolean natural) {
        if (ended) {
            return;
        }
        ended = true;

        if (natural && currentHp > 0) {
            healAndKnockback();
        }
        Entity owner = this.getOwner();
        if (owner instanceof LivingEntity livingOwner) {
            livingOwner.removeTag(CrystalHydroDomeConstants.DOME_TAG);
        }
        // Early end (HP 0, caster moved out, dead, logout): cancel the in-progress CONTINUOUS
        // cast so the cast bar disappears instead of lingering until its own tick 120. Safe to
        // call unconditionally — Utils.serverSideCancelCast (CancelCastPacket.cancelCast,
        // decompiled) itself no-ops if the player isn't currently casting. This synchronously
        // calls back into CrystalHydroDomeSpell.onServerCastComplete(cancelled=true), which is
        // exactly why `ended` above must already be true before this line.
        if (!natural && owner instanceof ServerPlayer serverPlayer) {
            Utils.serverSideCancelCast(serverPlayer);
        }
        clearHpReadout();
        victimIFrames.clear();
        // Leave gameplay immediately (no more damage redirect, Counter, or projectile layer — all
        // lookups go through ACTIVE_DOMES), but keep the entity loaded for END_LINGER_TICKS so
        // clients can play the end animation; tick() discards it afterwards.
        ACTIVE_DOMES.remove(this);
        boolean broken = !natural || currentHp <= 0;
        this.entityData.set(DATA_END_STATE, broken ? END_STATE_BROKEN : END_STATE_NATURAL);
        playEndSound(broken);
    }

    /** Falling-shard tinkles after the main crash, so the break rings out instead of stopping dead. */
    private void playShatterTail(int tick) {
        double x = this.getX(), y = this.getY() + 2.0, z = this.getZ();
        if (tick == 3) {
            this.level().playSound(null, x, y, z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 2.0f, 1.3f);
            this.level().playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 2.0f, 1.2f);
        } else {
            this.level().playSound(null, x, y, z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.5f, 1.7f);
            this.level().playSound(null, x, y, z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.5f, 1.5f);
        }
    }

    private void playEndSound(boolean broken) {
        double x = this.getX(), y = this.getY() + 1.0, z = this.getZ();
        if (broken) {
            // Layered, since volume > 1 only widens hearing range in vanilla, it doesn't get louder:
            // low heavy crash + normal glass + Iron's ice-block impact + crystal crack.
            this.level().playSound(null, x, y, z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 3.0f, 0.55f);
            this.level().playSound(null, x, y, z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 3.0f, 0.9f);
            this.level().playSound(null, x, y, z, SoundRegistry.ICE_BLOCK_IMPACT.get(), SoundSource.PLAYERS, 3.0f, 0.9f);
            this.level().playSound(null, x, y, z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 3.0f, 0.7f);
        } else {
            this.level().playSound(null, x, y, z, SoundRegistry.HOLY_CAST.get(), SoundSource.PLAYERS, 1.5f, 1.2f);
            this.level().playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2.0f, 1.3f);
        }
    }

    private void healAndKnockback() {
        Vec3 center = this.position();

        for (LivingEntity target : gatherInside(LivingEntity.class)) {
            target.heal((float) CrystalHydroDomeConstants.END_HEAL);
        }

        double ringMargin = CrystalHydroDomeConstants.KNOCKBACK_RING_OUTER_RADIUS - CrystalHydroDomeConstants.RADIUS;
        AABB ringBox = searchBox(center, Math.max(ringMargin,
                CrystalHydroDomeConstants.KNOCKBACK_RING_ABOVE - CrystalHydroDomeConstants.HEIGHT));
        List<LivingEntity> ringTargets = this.level().getEntitiesOfClass(LivingEntity.class, ringBox,
                e -> !(e instanceof ArmorStand) && e.isAlive() && inKnockbackRing(center, e.position()));
        for (LivingEntity target : ringTargets) {
            Vec3 pos = target.position();
            // LivingEntity.knockback(strength, dx, dz) pushes the target AWAY from the point
            // (dx, dz) points toward — vanilla's own calls (LivingEntity.hurt(), l.1161-1168;
            // blockedByShield(), l.1214) always pass (source.pos - victim.pos), i.e. victim ->
            // source, and the target ends up moving away from source. For our ring, "source" is
            // the dome center, so the argument must point target -> center, not center -> target.
            double dx = center.x - pos.x;
            double dz = center.z - pos.z;
            target.knockback(CrystalHydroDomeConstants.KNOCKBACK_STRENGTH, dx, dz);
            // knockback() alone only updates server-side deltaMovement/hasImpulse; a player's
            // movement is client-authoritative, so without this the client's own prediction just
            // overwrites the invisible server-side velocity change next tick. Setting hurtMarked
            // is what vanilla's own hurt() relies on (via markHurt()) — ServerEntity.sendChanges()
            // (confirmed by decompile) generically checks this flag for every tracked entity every
            // tick and broadcasts a ClientboundSetEntityMotionPacket when it's set, then clears it.
            if (target instanceof ServerPlayer serverPlayer) {
                serverPlayer.hurtMarked = true;
            }
        }
    }

    private static boolean inKnockbackRing(Vec3 center, Vec3 point) {
        double dy = point.y - center.y;
        if (dy < CrystalHydroDomeConstants.KNOCKBACK_RING_BELOW || dy > CrystalHydroDomeConstants.KNOCKBACK_RING_ABOVE) {
            return false;
        }
        double dx = point.x - center.x;
        double dz = point.z - center.z;
        double horizDistSq = dx * dx + dz * dz;
        double inner = CrystalHydroDomeConstants.KNOCKBACK_RING_INNER_RADIUS;
        double outer = CrystalHydroDomeConstants.KNOCKBACK_RING_OUTER_RADIUS;
        return horizDistSq >= inner * inner && horizDistSq <= outer * outer;
    }

    // --- Shared cleanse helper (also used by CrystalHydroDomeSpell.onCast for the open cleanse,
    //     before the dome entity even exists) ---

    public static void cleanseHarmfulEffects(LivingEntity target) {
        for (MobEffectInstance instance : new ArrayList<>(target.getActiveEffects())) {
            if (instance.isInfiniteDuration()) {
                continue;
            }
            if (instance.getEffect().getCategory() != MobEffectCategory.HARMFUL) {
                continue;
            }
            target.removeEffect(instance.getEffect());
        }
        target.clearFire();
        target.setTicksFrozen(0);
    }
}
