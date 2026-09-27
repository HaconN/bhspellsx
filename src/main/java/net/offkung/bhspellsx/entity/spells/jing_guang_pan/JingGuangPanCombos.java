package net.offkung.bhspellsx.entity.spells.jing_guang_pan;

import com.p1nero.invincible.api.skill.ComboNode;
import com.p1nero.invincible.api.skill.ComboType;

public final class JingGuangPanCombos {
    /** Only KEY_1..4 actually bound to mouse-left; DODGE/WEAPON_INNATE are never selected. */
    public static boolean left(ComboType type, int mask) {
        var keys = new ComboType[] {ComboNode.ComboTypes.KEY_1, ComboNode.ComboTypes.KEY_2,
                ComboNode.ComboTypes.KEY_3, ComboNode.ComboTypes.KEY_4};
        for (int i = 0; i < keys.length; i++) if (type == keys[i]) return (mask & (1 << i)) != 0;
        return type != null && type.getSubTypes().stream().anyMatch(child -> left(child, mask));
    }
    private JingGuangPanCombos() {}
}
