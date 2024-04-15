package net.pierceth.pierceth_greatsword.skill;

import io.netty.buffer.Unpooled;
import net.minecraft.client.player.Input;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.pierceth.pierceth_greatsword.PiercethGreatsword;
import net.pierceth.pierceth_greatsword.world.capabilities.item.VOSSkillDataKeys;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yesman.epicfight.api.animation.property.AnimationProperty;
import yesman.epicfight.api.animation.types.EntityState;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.client.events.engine.ControllEngine;
import yesman.epicfight.client.world.capabilites.entitypatch.player.LocalPlayerPatch;
import yesman.epicfight.network.client.CPExecuteSkill;
import yesman.epicfight.skill.*;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.entity.eventlistener.BasicAttackEvent;
import yesman.epicfight.world.entity.eventlistener.ComboCounterHandleEvent;
import yesman.epicfight.world.entity.eventlistener.PlayerEventListener.EventType;
import yesman.epicfight.world.entity.eventlistener.SkillConsumeEvent;

import java.util.List;
import java.util.UUID;

public class DirectionalBasicAttack extends Skill {
    private static final UUID EVENT_UUID = UUID.fromString("52c3ecc9-3167-4217-bd7d-2fa889e3ec1f");

    public static Skill.Builder<DirectionalBasicAttack> createBasicAttackBuilder() {
        return (new Builder<DirectionalBasicAttack>()).setCategory(SkillCategories.BASIC_ATTACK).setActivateType(ActivateType.ONE_SHOT).setResource(Resource.NONE);
    }

    public static void setComboCounterWithEvent(ComboCounterHandleEvent.Causal reason, ServerPlayerPatch playerpatch, SkillContainer container, StaticAnimation causalAnimation, int value) {
        //int prevValue = container.getDataManager().getDataValue(VOSSkillDataKeys.DIRECTIONAL_COMBO_COUNTER.get());
        //ComboCounterHandleEvent comboResetEvent = new ComboCounterHandleEvent(reason, playerpatch, causalAnimation, prevValue, value);
        // container.getExecuter().getEventListener().triggerEvents(EventType.COMBO_COUNTER_HANDLE_EVENT, comboResetEvent);
        //container.getDataManager().setData(VOSSkillDataKeys.DIRECTIONAL_COMBO_COUNTER.get(), comboResetEvent.getNextValue());
    }

    public DirectionalBasicAttack(Builder<? extends Skill> builder) {
        super(builder);
    }

    @Override
    public void onInitiate(SkillContainer container) {
        container.getExecuter().getEventListener().addEventListener(EventType.ACTION_EVENT_SERVER, EVENT_UUID, (event) -> {
            if (!event.getAnimation().isBasicAttackAnimation() && event.getAnimation().getProperty(AnimationProperty.ActionAnimationProperty.RESET_PLAYER_COMBO_COUNTER).orElse(true)) {
                CapabilityItem item = event.getPlayerPatch().getHoldingItemCapability(InteractionHand.MAIN_HAND);

                if (item.shouldCancelCombo(event.getPlayerPatch())) {
                    setComboCounterWithEvent(ComboCounterHandleEvent.Causal.ACTION_ANIMATION_RESET, event.getPlayerPatch(), container, event.getAnimation(), 0);
                }
            }
        });
    }

    @Override
    public void onRemoved(SkillContainer container) {
        container.getExecuter().getEventListener().removeListener(EventType.ACTION_EVENT_SERVER, EVENT_UUID);
    }

    @Override
    public boolean isExecutableState(PlayerPatch<?> executer) {
        EntityState playerState = executer.getEntityState();
        Player player = executer.getOriginal();

        return !(player.isSpectator() || executer.isUnstable() || !playerState.canBasicAttack());
    }

    @Override
    public void executeOnServer(ServerPlayerPatch executer, FriendlyByteBuf args) {
        Logger logger = LogManager.getLogger(PiercethGreatsword.MODID);

        SkillConsumeEvent event = new SkillConsumeEvent(executer, this, this.resource, true);
        executer.getEventListener().triggerEvents(EventType.SKILL_CONSUME_EVENT, event);

        if (!event.isCanceled()) {
            event.getResourceType().consumer.consume(this, executer, event.getAmount());
        }

        if (executer.getEventListener().triggerEvents(EventType.BASIC_ATTACK_EVENT, new BasicAttackEvent(executer))) {
            return;
        }

        CapabilityItem cap = executer.getHoldingItemCapability(InteractionHand.MAIN_HAND);
        StaticAnimation attackMotion = null;
        ServerPlayer player = executer.getOriginal();
        SkillContainer skillContainer = executer.getSkill(this);
        SkillDataManager dataManager = skillContainer.getDataManager();
        //int comboCounter = dataManager.getDataValue(VOSSkillDataKeys.DIRECTIONAL_COMBO_COUNTER.get());
        int comboCounter = 0;

        logger.debug("wow");

        if (player.isPassenger()) {
            Entity entity = player.getVehicle();

            if ((entity instanceof PlayerRideableJumping ridable && ridable.canJump()) && cap.availableOnHorse() && cap.getMountAttackMotion() != null) {
                comboCounter %= cap.getMountAttackMotion().size();
                attackMotion = cap.getMountAttackMotion().get(comboCounter);
                comboCounter++;
            }
        } else {
            int fw = args.readInt();
            int sw = args.readInt();

            List<StaticAnimation> combo = cap.getAutoAttckMotion(executer);
            int comboSize = combo.size();
            boolean dashAttack = player.isSprinting();

            logger.debug("Combo Size:" + comboSize);

            if (dashAttack) {
                // Dash Attack
                comboCounter = comboSize - 2;
            }
            else if(sw == -1) {
                // Right Attack
                comboCounter = 0;
            }
            else if(sw == 1) {
                // Left Attack
                comboCounter = 0;
            }
            else if(fw == -1) {
                // Back Attack
                comboCounter = 0;
            }
            else {
                // Normal Attack
                comboCounter %= comboSize - 2;
            }

            attackMotion = combo.get(comboCounter);
            comboCounter = dashAttack ? 0 : comboCounter + 1;
        }

        setComboCounterWithEvent(ComboCounterHandleEvent.Causal.ACTION_ANIMATION_RESET, executer, skillContainer, attackMotion, comboCounter);

        if (attackMotion != null) {
            executer.playAnimationSynchronized(attackMotion, 0);
        }

        executer.updateEntityState();
    }

    @Override
    public void updateContainer(SkillContainer container) {
        //if (!container.getExecuter().isLogicalClient() && container.getExecuter().getTickSinceLastAction() > 16 && container.getDataManager().getDataValue(SkillDataKeys.COMBO_COUNTER.get()) > 0) {
        //    setComboCounterWithEvent(ComboCounterHandleEvent.Causal.TIME_EXPIRED_RESET, (ServerPlayerPatch)container.getExecuter(), container, null, 0);
        //}
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public FriendlyByteBuf gatherArguments(LocalPlayerPatch executer, ControllEngine controllEngine) {
        Input input = executer.getOriginal().input;
        float pulse = Mth.clamp(0.3F + EnchantmentHelper.getSneakingSpeedBonus(executer.getOriginal()), 0.0F, 1.0F);
        input.tick(false, pulse);

        int forward = input.up ? 1 : 0;
        int backward = input.down ? -1 : 0;
        int left = input.left ? 1 : 0;
        int right = input.right ? -1 : 0;

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(forward);
        buf.writeInt(backward);
        buf.writeInt(left);
        buf.writeInt(right);

        return buf;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public Object getExecutionPacket(LocalPlayerPatch executer, FriendlyByteBuf args) {
        int forward = args.readInt();
        int backward = args.readInt();
        int left = args.readInt();
        int right = args.readInt();
        int vertic = forward + backward;
        int horizon = left + right;

        CPExecuteSkill packet = new CPExecuteSkill(executer.getSkill(this).getSlotId());
        packet.getBuffer().writeInt(Integer.compare(vertic, 0));
        packet.getBuffer().writeInt(Integer.compare(horizon, 0));

        return packet;
    }
}