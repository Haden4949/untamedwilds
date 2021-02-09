package untamedwilds.item.debug;

import net.minecraft.entity.AgeableEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import untamedwilds.config.ConfigGamerules;
import untamedwilds.entity.ComplexMob;
import untamedwilds.entity.ComplexMobTerrestrial;
import untamedwilds.entity.ISpecies;
import untamedwilds.util.TimeUtils;

public class ItemAnalyzer extends Item {

    public ItemAnalyzer(Properties properties) {
        super(properties);
    }

    @Override
    public ActionResultType itemInteractionForEntity(ItemStack stack, PlayerEntity playerIn, LivingEntity target, Hand hand) {
        World world = target.getEntityWorld();
        if (world.isRemote) return ActionResultType.PASS;
        if (target instanceof ComplexMob) {
            ComplexMob entity = (ComplexMob)target;
            if (entity instanceof ISpecies) {
                playerIn.sendMessage(new StringTextComponent("Diagnose: " + entity.getGenderString() + " " + ((ISpecies) entity).getSpeciesName() + " " + entity.getHealth() + "/" + entity.getMaxHealth() + " HP"), playerIn.getUniqueID());
            }
            else {
                playerIn.sendMessage(new StringTextComponent("Diagnose: " + entity.getGenderString() + " " + entity.getName().getString() + " " + entity.getHealth() + "/" + entity.getMaxHealth() + " HP"), playerIn.getUniqueID());
            }

            if (ConfigGamerules.scientificNames.get()) {
                if (entity instanceof ISpecies) {
                    playerIn.sendMessage(new TranslationTextComponent(entity.getType().getTranslationKey() + "_" + ((ISpecies) entity).getRawSpeciesName() + ".sciname").mergeStyle(TextFormatting.ITALIC), playerIn.getUniqueID());
                }
                else {
                    playerIn.sendMessage(new TranslationTextComponent(entity.getType().getTranslationKey() + ".sciname").mergeStyle(TextFormatting.ITALIC), playerIn.getUniqueID());
                }
            }
            if (target instanceof ComplexMobTerrestrial) {
                playerIn.sendMessage(new StringTextComponent("Hunger: " + ((ComplexMobTerrestrial)entity).getHunger() + "/100 Hunger"), playerIn.getUniqueID());
                if (!entity.isMale() && entity.getGrowingAge() > 0 && !ConfigGamerules.easyBreeding.get()) {
                    playerIn.sendMessage(new StringTextComponent("This female will give birth in " + TimeUtils.convertTicksToDays(world, entity.getGrowingAge()) + " (" + entity.getGrowingAge() + " ticks)"), playerIn.getUniqueID());
                }
            }
            if (entity.wantsToBreed()) {
                playerIn.sendMessage(new StringTextComponent("This mob is looking for a suitable mate"), playerIn.getUniqueID());
            }
            if (entity.isChild()) {
                playerIn.sendMessage(new StringTextComponent("This mob will grow up in " + TimeUtils.convertTicksToDays(world, entity.getGrowingAge() * -1) + " (" + entity.getGrowingAge() * -1 + " ticks)"), playerIn.getUniqueID());
            }
            //playerIn.sendMessage(new StringTextComponent("This mob will naturally despawn: " + !entity.preventDespawn()));
            return ActionResultType.SUCCESS;
        }
        else {
            playerIn.sendMessage(new StringTextComponent("Diagnose: " + target.getName().getString() + " " + target.getHealth() + "/" + target.getMaxHealth() + " HP"), playerIn.getUniqueID());
            if (target.isChild() && target instanceof AgeableEntity) {
                AgeableEntity entity = (AgeableEntity) target;
                playerIn.sendMessage(new StringTextComponent("This mob will grow up in " + TimeUtils.convertTicksToDays(world, entity.getGrowingAge() * -1) + " (" + entity.getGrowingAge() * -1 + " ticks)"), playerIn.getUniqueID());
            }
            return ActionResultType.SUCCESS;
        }
    }
}