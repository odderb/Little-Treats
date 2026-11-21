package com.odder.littletreat.mixin.cookingforblockheads;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Blockheads mixins aren't required, so a Blockheads update that moves things only logs a warning rather than
 * crashing the game. This records which of them really took effect, for {@code BlockheadsMixinStatus} to check.
 *
 * <p>Results go in system properties because Mixin loads this class apart from the game's own copy of our classes,
 * so a static field set here wouldn't be seen. This class must not touch any game classes.
 */
public class BlockheadsMixinPlugin implements IMixinConfigPlugin {
    public static final String PROPERTY_PREFIX = "littletreat.mixin.applied.";

    /**
     * Each mixin and the member that proves it worked: a handler the target must now call, or for the accessor the
     * method it must now have
     */
    private static final Map<String, String> HANDLER_CALLS = Map.of(
            "BlockheadsOvenMixin", "littletreat$getSmeltingResult",
            "BlockheadsToasterRecipeMixin", "littletreat$assemble",
            "SortButtonMixin", "littletreat$showDirection"
    );
    private static final Map<String, String> ADDED_METHODS = Map.of(
            "KitchenMenuAccessor", "littletreat$getCurrentSorting"
    );

    /**
     * Only called when the mixin applied without errors. An injection whose target wasn't found still gets here, so
     * the target is checked for the call itself.
     */
    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        String mixin = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);

        boolean applied;
        if (HANDLER_CALLS.containsKey(mixin)) {
            applied = callsHandler(targetClass, HANDLER_CALLS.get(mixin));
        } else if (ADDED_METHODS.containsKey(mixin)) {
            applied = targetClass.methods.stream().anyMatch(method -> method.name.equals(ADDED_METHODS.get(mixin)));
        } else {
            // A new mixin was added without a check here, it can't be vouched for
            applied = false;
        }

        if (applied) {
            System.setProperty(PROPERTY_PREFIX + mixin, "true");
        }
    }

    /**
     * Mixin renames handlers as it merges them, but keeps the original name at the end
     */
    private static boolean callsHandler(ClassNode targetClass, String handler) {
        for (MethodNode method : targetClass.methods) {
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode call && call.owner.equals(targetClass.name) && call.name.endsWith(handler)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
