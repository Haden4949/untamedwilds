package untamedwilds.entity.reptile;

import com.github.alexthe666.citadel.animation.Animation;
import net.minecraft.block.BlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.goal.HurtByTargetGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.server.ServerWorld;
import untamedwilds.config.ConfigGamerules;
import untamedwilds.entity.ComplexMobTerrestrial;
import untamedwilds.entity.INewSkins;
import untamedwilds.entity.ISpecies;
import untamedwilds.entity.ai.SmartAvoidGoal;
import untamedwilds.entity.ai.SmartMateGoal;
import untamedwilds.entity.ai.SmartSwimGoal;
import untamedwilds.entity.ai.SmartWanderGoal;
import untamedwilds.entity.ai.target.DontThreadOnMeTarget;
import untamedwilds.entity.ai.target.HuntMobTarget;
import untamedwilds.init.ModItems;
import untamedwilds.init.ModSounds;
import untamedwilds.util.EntityUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EntitySnake extends ComplexMobTerrestrial implements ISpecies, INewSkins {

    private static final int GROWING = 6 * ConfigGamerules.cycleLength.get();
    private static final String BREEDING = "LATE_SUMMER";
    public static Animation ANIMATION_TONGUE;

    public EntitySnake(EntityType<? extends ComplexMobTerrestrial> type, World worldIn) {
        super(type, worldIn);
        ANIMATION_TONGUE = Animation.create(10);
        this.ticksToSit = 20;
    }

    @Override
    protected void registerData() {
        super.registerData();
        this.entityCollisionReduction = 1F;
    }

    public static AttributeModifierMap.MutableAttribute registerAttributes() {
        return MobEntity.func_233666_p_()
                .createMutableAttribute(Attributes.ATTACK_DAMAGE, 2.0D)
                .createMutableAttribute(Attributes.MOVEMENT_SPEED, 0.33D)
                .createMutableAttribute(Attributes.FOLLOW_RANGE, 12.0D)
                .createMutableAttribute(Attributes.MAX_HEALTH, 5.0D)
                .createMutableAttribute(Attributes.KNOCKBACK_RESISTANCE, 0D)
                .createMutableAttribute(Attributes.ARMOR, 0D);
    }

    public void registerGoals() {
        this.goalSelector.addGoal(1, new SmartSwimGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.3D, false));
        this.goalSelector.addGoal(2, new SmartMateGoal(this, 1D));
        this.goalSelector.addGoal(2, new SmartAvoidGoal<>(this, LivingEntity.class, 16, 1.2D, 1.6D, input -> getEcoLevel(input) > 6));
        this.goalSelector.addGoal(3, new SmartWanderGoal(this, 1.0D, true));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(3, new HuntMobTarget<>(this, LivingEntity.class, true, 30, false, input -> getEcoLevel(input) < 4));
        this.targetSelector.addGoal(3, new DontThreadOnMeTarget<>(this, LivingEntity.class, true));
    }

    public static void processSkins() {
        for (int i = 0; i < SpeciesSnake.values().length; i++) {
            EntityUtils.buildSkinArrays("snake", SpeciesSnake.values()[i].name().toLowerCase(), i, TEXTURES_COMMON, TEXTURES_RARE);
        }
    }

    public boolean preventDespawn() {
        return false;
    }

    public void livingTick() {
        super.livingTick();
        if (!this.world.isRemote) {
            if (this.ticksExisted % 1000 == 0) {
                if (this.wantsToBreed() && !this.isMale()) {
                    this.breed();
                }
                if (this.rand.nextInt(40) == 0) {
                    this.entityDropItem(new ItemStack(ModItems.MATERIAL_SNAKE_SKIN.get()), 0.2F);
                }
            }
            if (this.world.getGameTime() % 4000 == 0) {
                this.heal(1.0F);
            }
            if (this.ticksExisted % 120 == 0) {
                this.setAnimation(ANIMATION_TONGUE);
            }
            if (this.getAnimation() == NO_ANIMATION && this.getAttackTarget() == null && !this.isSleeping()) {
                int i = this.rand.nextInt(3000);
                if (i <= 10 && !this.isInWater() && this.isNotMoving() && this.canMove()) {
                    this.getNavigator().clearPath();
                    this.setSitting(true);
                }
                if ((i == 11 || this.isInWater() || this.isActive()) && this.isSitting()) {
                    this.setSitting(false);
                }
            }
            this.setAngry(this.getAttackTarget() != null);
        }
    }

    /* Breeding conditions for the Snake are:
     * A nearby Snake of the opposite gender and the same species */
    public boolean wantsToBreed() {
        if (super.wantsToBreed()) {
            if (!this.isSleeping() && this.getGrowingAge() == 0 && EntityUtils.hasFullHealth(this)) {
                List<EntitySnake> list = this.world.getEntitiesWithinAABB(EntitySnake.class, this.getBoundingBox().grow(6.0D, 4.0D, 6.0D));
                list.removeIf(input -> EntityUtils.isInvalidPartner(this, input, false));
                if (list.size() >= 1) {
                    this.setGrowingAge(GROWING);
                    list.get(0).setGrowingAge(GROWING);
                    return true;
                }
            }
        }
        return false;
    }

    protected void playStepSound(BlockPos pos, BlockState blockIn) {
    }

    protected SoundEvent getAmbientSound() {
        if (this.isAngry()) {
            if (this.isRattler()) {
                return ModSounds.ENTITY_SNAKE_RATTLE;
            }
            return ModSounds.ENTITY_SNAKE_HISS;
        }
        return null;
    }

    protected SoundEvent getHurtSound(DamageSource source) {
        if (this.isRattler()) {
            return ModSounds.ENTITY_SNAKE_RATTLE;
        }
        return ModSounds.ENTITY_SNAKE_HISS;
    }

    protected SoundEvent getDeathSound() {
        return ModSounds.ENTITY_SNAKE_HISS;
    }

    @Nullable
    @Override
    public AgeableEntity func_241840_a(ServerWorld serverWorld, AgeableEntity ageableEntity) {
        EntityUtils.dropEggs(this, "egg_snake_" + getRawSpeciesName(this.getVariant()).toLowerCase(), 4);
        return null;
    }

    @Override
    public ActionResultType func_230254_b_(PlayerEntity player, Hand hand) {
        ItemStack itemstack = player.getHeldItem(Hand.MAIN_HAND);

        if (itemstack.isEmpty() && this.isAlive()) {
            EntityUtils.turnEntityIntoItem(this,"snake_" + getRawSpeciesName(this.getVariant()).toLowerCase());
            return ActionResultType.func_233537_a_(this.world.isRemote);
        }
        return super.func_230254_b_(player, hand);
    }

    public String getBreedingSeason() {
        return BREEDING;
    }
    public int getAdulthoodTime() { return GROWING; }

    public boolean isBreedingItem(ItemStack stack) { return stack.getItem() == Items.RABBIT; }

    @Override
    public int setSpeciesByBiome(RegistryKey<Biome> biomeKey, Biome biome, SpawnReason reason) {
        if (ConfigGamerules.randomSpecies.get() || isArtificialSpawnReason(reason)) {
            return this.rand.nextInt(EntitySnake.SpeciesSnake.values().length);
        }
        return EntitySnake.SpeciesSnake.getSpeciesByBiome(biome);
    }

    protected activityType getActivityType() {
        return activityType.INSOMNIAC;
    }
    public String getSpeciesName(int i) { return new TranslationTextComponent("entity.untamedwilds.snake_" + getRawSpeciesName(i)).getString(); }
    public String getRawSpeciesName(int i) { return SpeciesSnake.values()[i].name().toLowerCase(); }

    public boolean isRattler() { return SpeciesSnake.values()[this.getVariant()].isRattler(); }

    @Override
    public Animation[] getAnimations() {
        return new Animation[]{NO_ANIMATION, ANIMATION_TONGUE};
    }

    public boolean attackEntityAsMob(Entity entityIn) {
        float f = (float)this.getAttribute(Attributes.ATTACK_DAMAGE).getValue();
        boolean flag = entityIn.attackEntityFrom(DamageSource.causeMobDamage(this), f);
        if (flag && SpeciesSnake.values()[this.getVariant()].getVenomTier() > 0) {
            if (entityIn instanceof LivingEntity) {
                ((LivingEntity)entityIn).addPotionEffect(new EffectInstance(Effects.POISON, 200, SpeciesSnake.values()[this.getVariant()].getVenomTier() - 1));
            }
            return true;
        }
        return flag;
    }

    public enum SpeciesSnake implements IStringSerializable {

        ADDER			(0, 0.7F,	1,	false, 3, Biome.Category.TAIGA, Biome.Category.FOREST, Biome.Category.PLAINS),
        BALL_PYTHON		(1, 1.0F,	0,	false, 2, Biome.Category.SAVANNA),
        BLACK_MAMBA		(2, 1.3F,	2,	false, 2, Biome.Category.SAVANNA),
        CARPET_PYTHON	(3, 1.1F,	0,	false, 1, Biome.Category.JUNGLE),
        CAVE_RACER	    (4, 1.1F,	0,	false, 3, Biome.Category.JUNGLE),
        CORAL			(5, 0.7F,	2,	false, 3, Biome.Category.MESA, Biome.Category.FOREST, Biome.Category.PLAINS),
        CORAL_BLUE		(6, 1.1F,	1,	false, 1, Biome.Category.JUNGLE),
        CORN			(7, 0.9F,	0,	false, 3, Biome.Category.PLAINS),
        EMERALD			(8, 0.9F,	0,	false, 2, Biome.Category.JUNGLE),
        GRASS_SNAKE		(9, 0.8F,	0,	false, 3, Biome.Category.SWAMP, Biome.Category.FOREST),
        GRAY_KINGSNAKE	(10, 0.7F,	0,	false, 3, Biome.Category.MESA),
        GREEN_MAMBA		(11, 1.3F,	2,	false, 2, Biome.Category.SAVANNA, Biome.Category.JUNGLE),
        RATTLESNAKE		(12,0.7F,	1,	true,  2, Biome.Category.MESA, Biome.Category.PLAINS),
        RICE_PADDY		(13,0.7F,	1,	false,  2, Biome.Category.FOREST, Biome.Category.SWAMP),
        SWAMP_MOCCASIN	(14,0.7F,	1,	false, 2, Biome.Category.SWAMP),
        TAIPAN		    (15, 1.0F,	2,	false, 2, Biome.Category.MESA),
        WESTERN_RATTLESNAKE	(16,0.7F,	1,	true,  2, Biome.Category.MESA);
        
        public Float scale;
        public int species;
        public int rolls;
        public int venomTier;
        public boolean rattler;
        public Biome.Category[] spawnBiomes;

        SpeciesSnake(int species, Float scale, int venomTier, boolean rattler, int rolls, Biome.Category... biomes) {
            this.species = species;
            this.scale = scale;
            this.rolls = rolls;
            this.venomTier = venomTier;
            this.rattler = rattler;
            this.spawnBiomes = biomes;
        }

        public int getSpecies() { return this.species; }

        public String getString() {
            return I18n.format("entity.tarantula." + this.name().toLowerCase());
        }

        public static int getSpeciesByBiome(Biome biome) {
            List<SpeciesSnake> types = new ArrayList<>();
            for (SpeciesSnake type : values()) {
                for(Biome.Category biomeTypes : type.spawnBiomes) {
                    if(biome.getCategory() == biomeTypes){
                        for (int i=0; i < type.rolls; i++) {
                            types.add(type);
                        }
                    }
                }
            }
            if (types.isEmpty()) {
                return 99;
            } else {
                return types.get(new Random().nextInt(types.size())).getSpecies();
            }
        }

        public int getVenomTier() {
            return venomTier;
        }

        public boolean isRattler() {
            return this.rattler;
        }
    }
}