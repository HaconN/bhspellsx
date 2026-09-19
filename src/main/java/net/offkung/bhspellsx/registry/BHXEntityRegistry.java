package net.offkung.bhspellsx.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.offkung.bhspellsx.entity.spells.amethyst_decree.AmethystDecreeAoe;
import net.offkung.bhspellsx.entity.spells.amethyst_decree.AmethystDecreeCasterRingEntity;
import net.offkung.bhspellsx.entity.spells.amethyst_decree.AmethystDecreeTargetCrystalEntity;
import net.offkung.bhspellsx.entity.spells.crystal_hydro_dome.CrystalHydroDomeAoe;
import net.offkung.bhspellsx.entity.spells.embracing_bosom.EmbracingBosomAoe;
import net.offkung.bhspellsx.entity.spells.xian_she_huan_ying.XianSheHuanYingTargetEntity;
import net.offkung.bhspellsx.entity.spells.xian_she_huan_ying.XianSheHuanYingUserEntity;

public class BHXEntityRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "bhspellsx");

    public static final RegistryObject<EntityType<EmbracingBosomAoe>> EMBRACING_BOSOM_AOE =
            ENTITY_TYPES.register("embracing_bosom_aoe", () -> EntityType.Builder
                    .<EmbracingBosomAoe>of(EmbracingBosomAoe::new, MobCategory.MISC)
                    .sized(12.0f, 1.2f)
                    .clientTrackingRange(64)
                    .build(ResourceLocation.fromNamespaceAndPath("bhspellsx", "embracing_bosom_aoe").toString()));

    // Sized to cover the burst radius (RADIUS in config, default 6.75) so the entity's own
    // bounding box (used for target selection) reaches every valid target without relying on
    // vanilla's tracking-range culling to have already loaded them.
    public static final RegistryObject<EntityType<AmethystDecreeAoe>> AMETHYST_DECREE_AOE =
            ENTITY_TYPES.register("amethyst_decree_aoe", () -> EntityType.Builder
                    .<AmethystDecreeAoe>of(AmethystDecreeAoe::new, MobCategory.MISC)
                    .sized(22.0f, 1.2f)
                    .clientTrackingRange(64)
                    .build(ResourceLocation.fromNamespaceAndPath("bhspellsx", "amethyst_decree_aoe").toString()));

    // Phase 2 VFX. Pure decorative entities (no damage/targeting) — see the entity classes'
    // javadocs. Sized generously to cover their crystal spread so the client doesn't cull the
    // entity (and therefore its renderer) while crystals are still visible off to the side.
    public static final RegistryObject<EntityType<AmethystDecreeCasterRingEntity>> AMETHYST_DECREE_CASTER_RING =
            ENTITY_TYPES.register("amethyst_decree_caster_ring", () -> EntityType.Builder
                    .<AmethystDecreeCasterRingEntity>of(AmethystDecreeCasterRingEntity::new, MobCategory.MISC)
                    .sized(22.0f, 4.0f)
                    .clientTrackingRange(64)
                    .build(ResourceLocation.fromNamespaceAndPath("bhspellsx", "amethyst_decree_caster_ring").toString()));

    public static final RegistryObject<EntityType<AmethystDecreeTargetCrystalEntity>> AMETHYST_DECREE_TARGET_CRYSTAL =
            ENTITY_TYPES.register("amethyst_decree_target_crystal", () -> EntityType.Builder
                    .<AmethystDecreeTargetCrystalEntity>of(AmethystDecreeTargetCrystalEntity::new, MobCategory.MISC)
                    .sized(2.0f, 3.0f)
                    .clientTrackingRange(64)
                    .build(ResourceLocation.fromNamespaceAndPath("bhspellsx", "amethyst_decree_target_crystal").toString()));

    // Registry baseline only (sized back when the dome radius was 5). AoeEntity.getDimensions()
    // overrides this from getRadius() at runtime (height hardcoded 1.2), and nothing in the dome
    // relies on the bounding box: gameplay uses CrystalHydroDomeAoe.isInside()/searchBox() and
    // the renderer's shouldRender() always returns true.
    public static final RegistryObject<EntityType<CrystalHydroDomeAoe>> CRYSTAL_HYDRO_DOME_AOE =
            ENTITY_TYPES.register("crystal_hydro_dome_aoe", () -> EntityType.Builder
                    .<CrystalHydroDomeAoe>of(CrystalHydroDomeAoe::new, MobCategory.MISC)
                    .sized(12.0f, 6.0f)
                    .clientTrackingRange(64)
                    .build(ResourceLocation.fromNamespaceAndPath("bhspellsx", "crystal_hydro_dome_aoe").toString()));

    // Xian She Huan Ying: decorative/lifecycle entities, no hitbox that matters (Entity, not a
    // Projectile). Tracking range matches amethyst_decree.
    public static final RegistryObject<EntityType<XianSheHuanYingUserEntity>> XIAN_SHE_HUAN_YING_USER =
            ENTITY_TYPES.register("xian_she_huan_ying_user", () -> EntityType.Builder
                    .<XianSheHuanYingUserEntity>of(XianSheHuanYingUserEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(64)
                    .build(ResourceLocation.fromNamespaceAndPath("bhspellsx", "xian_she_huan_ying_user").toString()));

    public static final RegistryObject<EntityType<XianSheHuanYingTargetEntity>> XIAN_SHE_HUAN_YING_TARGET =
            ENTITY_TYPES.register("xian_she_huan_ying_target", () -> EntityType.Builder
                    .<XianSheHuanYingTargetEntity>of(XianSheHuanYingTargetEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(64)
                    .build(ResourceLocation.fromNamespaceAndPath("bhspellsx", "xian_she_huan_ying_target").toString()));

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
