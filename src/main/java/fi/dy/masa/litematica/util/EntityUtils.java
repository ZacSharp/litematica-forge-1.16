package fi.dy.masa.litematica.util;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import com.google.common.base.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Direction;
import net.minecraft.world.World;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import fi.dy.masa.malilib.util.Constants;
import fi.dy.masa.malilib.util.InventoryUtils;

public class EntityUtils
{
    public static final Predicate<Entity> NOT_PLAYER = new Predicate<Entity>()
    {
        @Override
        public boolean apply(@Nullable Entity entity)
        {
            return (entity instanceof PlayerEntity) == false;
        }
    };

    public static boolean hasToolItem(LivingEntity entity)
    {
        return hasToolItemInHand(entity, Hand.MAIN_HAND) ||
               hasToolItemInHand(entity, Hand.OFF_HAND);
    }

    public static boolean hasToolItemInHand(LivingEntity entity, Hand hand)
    {
        // If the configured tool item has NBT data, then the NBT is compared, otherwise it's ignored

        ItemStack toolItem = DataManager.getToolItem();

        if (toolItem.isEmpty())
        {
            return entity.getHeldItemMainhand().isEmpty();
        }

        ItemStack stackHand = entity.getHeldItem(hand);

        if (ItemStack.areItemsEqual(toolItem, stackHand))
        {
            return toolItem.hasTag() == false || ItemUtils.areTagsEqualIgnoreDamage(toolItem, stackHand);
        }

        return false;
    }

    /**
     * Checks if the requested item is currently in the player's hand such that it would be used for using/placing.
     * This means, that it must either be in the main hand, or the main hand must be empty and the item is in the offhand.
     * @param player
     * @param stack
     * @return
     */
    @Nullable
    public static Hand getUsedHandForItem(PlayerEntity player, ItemStack stack)
    {
        Hand hand = null;

        if (InventoryUtils.areStacksEqual(player.getHeldItemMainhand(), stack))
        {
            hand = Hand.MAIN_HAND;
        }
        else if (player.getHeldItemMainhand().isEmpty() &&
                 InventoryUtils.areStacksEqual(player.getHeldItemOffhand(), stack))
        {
            hand = Hand.OFF_HAND;
        }

        return hand;
    }

    public static boolean areStacksEqualIgnoreDurability(ItemStack stack1, ItemStack stack2)
    {
        return ItemStack.areItemsEqual(stack1, stack2) && ItemStack.areItemStackTagsEqual(stack1, stack2);
    }

    public static Direction getHorizontalLookingDirection(Entity entity)
    {
        return Direction.fromAngle(entity.rotationYaw);
    }

    public static Direction getVerticalLookingDirection(Entity entity)
    {
        return entity.rotationPitch > 0 ? Direction.DOWN : Direction.UP;
    }

    public static Direction getClosestLookingDirection(Entity entity)
    {
        if (entity.rotationPitch > 60.0f)
        {
            return Direction.DOWN;
        }
        else if (-entity.rotationPitch > 60.0f)
        {
            return Direction.UP;
        }

        return getHorizontalLookingDirection(entity);
    }

    @Nullable
    public static <T extends Entity> T findEntityByUUID(List<T> list, UUID uuid)
    {
        if (uuid == null)
        {
            return null;
        }

        for (T entity : list)
        {
            if (entity.getUniqueID().equals(uuid))
            {
                return entity;
            }
        }

        return null;
    }

    @Nullable
    public static String getEntityId(Entity entity)
    {
        EntityType<?> entitytype = entity.getType();
        ResourceLocation resourcelocation = EntityType.getKey(entitytype);
        return entitytype.isSerializable() && resourcelocation != null ? resourcelocation.toString() : null;
    }

    @Nullable
    private static Entity createEntityFromNBTSingle(CompoundNBT nbt, World world)
    {
        try
        {
            Optional<Entity> optional = EntityType.loadEntityUnchecked(nbt, world);

            if (optional.isPresent())
            {
                Entity entity = optional.get();
                entity.setUniqueId(UUID.randomUUID());
                return entity;
            }
        }
        catch (Exception e)
        {
        }

        return null;
    }

    /**
     * Note: This does NOT spawn any of the entities in the world!
     * @param nbt
     * @param world
     * @return
     */
    @Nullable
    public static Entity createEntityAndPassengersFromNBT(CompoundNBT nbt, World world)
    {
        Entity entity = createEntityFromNBTSingle(nbt, world);

        if (entity == null)
        {
            return null;
        }
        else
        {
            if (nbt.contains("Passengers", Constants.NBT.TAG_LIST))
            {
                ListNBT taglist = nbt.getList("Passengers", Constants.NBT.TAG_COMPOUND);

                for (int i = 0; i < taglist.size(); ++i)
                {
                    Entity passenger = createEntityAndPassengersFromNBT(taglist.getCompound(i), world);

                    if (passenger != null)
                    {
                        passenger.startRiding(entity, true);
                    }
                }
            }

            return entity;
        }
    }

    public static void spawnEntityAndPassengersInWorld(Entity entity, World world)
    {
        if (world.addEntity(entity) && entity.isBeingRidden())
        {
            for (Entity passenger : entity.getPassengers())
            {
                passenger.setLocationAndAngles(
                        entity.getPosX(),
                        entity.getPosY() + entity.getMountedYOffset() + passenger.getYOffset(),
                        entity.getPosZ(),
                        passenger.rotationYaw, passenger.rotationPitch);
                setEntityRotations(passenger, passenger.rotationYaw, passenger.rotationPitch);
                spawnEntityAndPassengersInWorld(passenger, world);
            }
        }
    }

    public static void setEntityRotations(Entity entity, float yaw, float pitch)
    {
        entity.rotationYaw = yaw;
        entity.prevRotationYaw = yaw;

        entity.rotationPitch = pitch;
        entity.prevRotationPitch = pitch;

        if (entity instanceof LivingEntity)
        {
            LivingEntity livingBase = (LivingEntity) entity;
            livingBase.rotationYawHead = yaw;
            livingBase.renderYawOffset = yaw;
            livingBase.prevRotationYawHead = yaw;
            livingBase.prevRenderYawOffset = yaw;
            //livingBase.renderYawOffset = yaw;
            //livingBase.prevRenderYawOffset = yaw;
        }
    }

    public static List<Entity> getEntitiesWithinSubRegion(World world, BlockPos origin, BlockPos regionPos, BlockPos regionSize,
            SchematicPlacement schematicPlacement, SubRegionPlacement placement)
    {
        // These are the untransformed relative positions
        BlockPos regionPosRelTransformed = PositionUtils.getTransformedBlockPos(regionPos, schematicPlacement.getMirror(), schematicPlacement.getRotation());
        BlockPos posEndAbs = PositionUtils.getTransformedPlacementPosition(regionSize.add(-1, -1, -1), schematicPlacement, placement).add(regionPosRelTransformed).add(origin);
        BlockPos regionPosAbs = regionPosRelTransformed.add(origin);
        net.minecraft.util.math.AxisAlignedBB bb = PositionUtils.createEnclosingAABB(regionPosAbs, posEndAbs);

        return world.getEntitiesInAABBexcluding((Entity) null, bb, null);
    }

    public static boolean shouldPickBlock(PlayerEntity player)
    {
        return Configs.Generic.PICK_BLOCK_ENABLED.getBooleanValue() &&
                (Configs.Generic.TOOL_ITEM_ENABLED.getBooleanValue() == false ||
                hasToolItem(player) == false) &&
                Configs.Visuals.ENABLE_RENDERING.getBooleanValue() &&
                Configs.Visuals.ENABLE_SCHEMATIC_RENDERING.getBooleanValue();
    }
}
