package net.offkung.bhspellsx.spells.gold;

import net.minecraft.resources.ResourceLocation;

/**
 * XSHY_TEST_ONLY — ลบก่อนส่ง. Thin subclass of the real spell used only to pick between the extra-VFX
 * variants in-game: same behavior, different spell id and different extra-VFX mode. The base
 * class's constructor never calls getSpellResource() (decompiled), so the id can safely live in a
 * subclass field.
 */
public class XianSheHuanYingTestSpell extends XianSheHuanYingSpell {
    private final ResourceLocation testSpellId;
    private final int testMode;

    public XianSheHuanYingTestSpell(String name, int mode) {
        this.testSpellId = ResourceLocation.fromNamespaceAndPath("bhspellsx", name);
        this.testMode = mode;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return this.testSpellId;
    }

    @Override
    protected int getExtraVfxMode() {
        return this.testMode;
    }
}
