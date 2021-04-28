package io.github.how_bout_no.outvoted.entity;

import com.google.common.collect.ImmutableList;
import io.github.how_bout_no.outvoted.Outvoted;
import io.github.how_bout_no.outvoted.entity.util.EntityUtils;
import io.github.how_bout_no.outvoted.init.ModItems;
import io.github.how_bout_no.outvoted.init.ModSounds;
import io.github.how_bout_no.outvoted.init.ModTags;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.*;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AirBlockItem;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.tag.BlockTags;
import net.minecraft.util.EightWayDirection;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.*;
import net.minecraft.world.explosion.Explosion;
import org.apache.commons.lang3.tuple.MutablePair;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.ParticleKeyFrameEvent;
import software.bernie.geckolib3.core.event.SoundKeyframeEvent;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class HungerEntity extends HostileEntity implements IAnimatable {
    private static final TrackedData<Boolean> BURROWED;
    private static final TrackedData<Boolean> ATTACKING;
    private static final TrackedData<Boolean> ENCHANTING;
    private static final TrackedData<Integer> VARIANT;
    private Map<Enchantment, Integer> storedEnchants = new HashMap<>();

    public HungerEntity(EntityType<? extends HungerEntity> type, World worldIn) {
        super(type, worldIn);
        this.experiencePoints = 5;
        EntityUtils.setConfigHealth(this, Outvoted.config.common.entities.hunger.health);
    }

    protected void initGoals() {
        this.goalSelector.add(1, new SwimGoal(this));
        this.goalSelector.add(2, new HungerEntity.BiteGoal(this, 1.0D, false));
        this.goalSelector.add(3, new HungerEntity.BurrowGoal(this));
        this.goalSelector.add(4, new HungerEntity.FindSpotGoal(this, 1.0D));
        this.goalSelector.add(5, new HungerEntity.WanderGoal(this));
        this.goalSelector.add(6, new HungerEntity.LookGoal(this));
        this.targetSelector.add(1, new FollowTargetGoal<>(this, LivingEntity.class, true));
    }

    public static DefaultAttributeContainer.Builder setCustomAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 12.0D)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25D);
    }

    @Override
    public float getPathfindingFavor(BlockPos pos, WorldView world) {
        return 0.0F;
    }

    public static boolean canSpawn(EntityType<HungerEntity> entity, WorldAccess world, SpawnReason spawnReason, BlockPos blockPos, Random random) {
        return world.getBaseLightLevel(blockPos, 0) > 8 && canMobSpawn(entity, world, spawnReason, blockPos, random) && world.getBlockState(blockPos.down()).isIn(ModTags.Blocks.HUNGER_CAN_BURROW);
    }

    @Override
    protected boolean isDisallowedInPeaceful() {
        return false;
    }

    protected SoundEvent getAmbientSound() {
        return ModSounds.HUNGER_AMBIENT.get();
    }

    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return ModSounds.HUNGER_HURT.get();
    }

    protected SoundEvent getDeathSound() {
        return ModSounds.HUNGER_DEATH.get();
    }

    public SoundCategory getSoundCategory() {
        return SoundCategory.HOSTILE;
    }

    public void writeCustomDataToTag(CompoundTag compound) {
        super.writeCustomDataToTag(compound);
        compound.putInt("Variant", this.getVariant());

        ItemStack item = ItemStack.EMPTY; // Store enchantments in an empty ItemStack
        EnchantmentHelper.set(storedEnchants, item);
        CompoundTag compoundNBT = new CompoundTag();
        item.toTag(compoundNBT);
        compound.put("Enchantments", compoundNBT);
    }

    /**
     * (abstract) Protected helper method to read subclass entity data from NBT.
     */
    public void readCustomDataFromTag(CompoundTag compound) {
        super.readCustomDataFromTag(compound);
        this.setVariant(compound.getInt("Variant"));

        ItemStack item = ItemStack.fromTag(compound.getCompound("Enchantments"));
        storedEnchants = EnchantmentHelper.fromTag(item.getEnchantments());
    }

    @Nullable
    public net.minecraft.entity.EntityData initialize(ServerWorldAccess worldIn, LocalDifficulty difficultyIn, SpawnReason reason, @Nullable net.minecraft.entity.EntityData spawnDataIn, @Nullable CompoundTag dataTag) {
        int type;
        if (worldIn.getBlockState(this.getVelocityAffectingPos()).getBlock().is(Blocks.SAND)) {
            type = 0;
        } else if (worldIn.getBlockState(this.getVelocityAffectingPos()).getBlock().is(Blocks.RED_SAND)) {
            type = 1;
        } else {
            type = 2;
        }
        this.setVariant(type);
        return super.initialize(worldIn, difficultyIn, reason, spawnDataIn, dataTag);
    }

    static {
        BURROWED = DataTracker.registerData(HungerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
        ATTACKING = DataTracker.registerData(HungerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
        ENCHANTING = DataTracker.registerData(HungerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
        VARIANT = DataTracker.registerData(HungerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(BURROWED, Boolean.FALSE);
        this.dataTracker.startTracking(ATTACKING, Boolean.FALSE);
        this.dataTracker.startTracking(ENCHANTING, Boolean.FALSE);
        this.dataTracker.startTracking(VARIANT, 0);
    }

    public void setBurrowed(boolean burrowed) {
        this.dataTracker.set(BURROWED, burrowed);
    }

    public boolean isBurrowed() {
        return this.dataTracker.get(BURROWED);
    }

    public void setAttacking(boolean attacking) {
        this.dataTracker.set(ATTACKING, attacking);
    }

    public boolean isAttacking() {
        return this.dataTracker.get(ATTACKING);
    }

    public void setEnchanting(boolean enchanting) {
        this.dataTracker.set(ENCHANTING, enchanting);
    }

    public boolean isEnchanting() {
        return this.dataTracker.get(ENCHANTING);
    }

    public void setVariant(int type) {
        this.dataTracker.set(VARIANT, type);
    }

    public int getVariant() {
        return this.dataTracker.get(VARIANT);
    }

    public MutablePair<Integer, ItemStack> modifyEnchantments(ItemStack stack, int damage, int count) {
        ItemStack itemstack = stack.copy();
        Map<Enchantment, Integer> cacheEnchants = new ConcurrentHashMap<>(storedEnchants);
        itemstack.removeSubTag("Enchantments");
        itemstack.removeSubTag("StoredEnchantments");
        if (damage > 0) {
            itemstack.setDamage(damage);
        } else {
            itemstack.removeSubTag("Damage");
        }

        if (itemstack.getItem() == Items.BOOK) {
            itemstack = new ItemStack(Items.ENCHANTED_BOOK);
        }

        /*
         * left: Status of item, 0 = accepted, 1 = rejected, 2 = enchanted
         * right: Item to return
         */
        MutablePair<Integer, ItemStack> pair = new MutablePair<>(0, itemstack);
        if (storedEnchants.size() <= Outvoted.config.common.entities.hunger.maxEnchants) {
            itemstack.setCount(count);
            final boolean[] hasCurses = {false};
            Map<Enchantment, Integer> map = EnchantmentHelper.get(stack).entrySet().stream().filter((enchant) -> {
                if (enchant.getKey().isCursed()) hasCurses[0] = true;
                return !enchant.getKey().isCursed();
                //return true;
            }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            if (hasCurses[0]) {
                this.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 600, 1));
                pair.setLeft(1);
                return pair;
            } else if (itemstack.getTag() != null && itemstack.getTag().contains("Bitten")) {
                pair.setLeft(1);
                return pair;
            } else if (!(itemstack.isEnchantable() || itemstack.hasEnchantments() || itemstack.getItem() instanceof EnchantedBookItem)) {
                pair.setRight(ItemStack.EMPTY);
                return pair;
            } else if (itemstack.getItem().equals(ModItems.VOID_HEART.get())) {
                return pair;
            }

            if (!map.isEmpty()) {
                for (Map.Entry<Enchantment, Integer> entry : map.entrySet()) {
                    Enchantment enchantment = entry.getKey();
                    Integer level = entry.getValue();
                    if (cacheEnchants.containsKey(enchantment)) {
                        if (enchantment.getMaxLevel() != 1 && cacheEnchants.get(enchantment) != enchantment.getMaxLevel() + 1) {
                            if (level == enchantment.getMaxLevel() && cacheEnchants.get(enchantment) == enchantment.getMaxLevel()) {
                                for (Enchantment ench : cacheEnchants.keySet()) {
                                    if (cacheEnchants.get(ench) == ench.getMaxLevel() + 1) {
                                        pair.setLeft(1);
                                        return pair;
                                    }
                                }
                                cacheEnchants.put(enchantment, enchantment.getMaxLevel() + 1);
                            } else if (level.equals(cacheEnchants.get(enchantment))) {
                                cacheEnchants.put(enchantment, level + 1);
                            } else if (level > cacheEnchants.get(enchantment)) {
                                cacheEnchants.put(enchantment, level);
                            }
                        } else {
                            pair.setLeft(1);
                            return pair;
                        }
                    } else if (!storedEnchants.isEmpty()) {
                        for (Enchantment ench : storedEnchants.keySet()) {
                            if (enchantment instanceof ProtectionEnchantment && ench instanceof ProtectionEnchantment) {
                                if (((ProtectionEnchantment) enchantment).canAccept(ench)) {
                                    cacheEnchants.put(enchantment, level);
                                } else {
                                    pair.setLeft(1);
                                    return pair;
                                }
                            } else if (enchantment instanceof InfinityEnchantment || enchantment instanceof MendingEnchantment) {
                                cacheEnchants.put(enchantment, level);
                            } else if (enchantment instanceof DamageEnchantment && ench instanceof DamageEnchantment) {
                                cacheEnchants.put(enchantment, level);
                            } else if (enchantment.canCombine(ench)) {
                                cacheEnchants.put(enchantment, level);
                            } else {
                                pair.setLeft(1);
                                return pair;
                            }
                        }
                    } else {
                        cacheEnchants.put(enchantment, level);
                    }
                }
            } else if (!storedEnchants.isEmpty()) {
                pair.setLeft(2);
                map = storedEnchants;
                storedEnchants = new HashMap<>();
                EnchantmentHelper.set(map, itemstack);
                return pair;
            } else {
                pair.setRight(ItemStack.EMPTY);
            }
            storedEnchants = cacheEnchants;
        } else {
            pair.setLeft(1);
        }
        return pair;
    }

    /**
     * Returns whether this Entity is invulnerable to the given DamageSource.
     *
     * @param source
     */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return super.isInvulnerableTo(source) && !source.name.equals("wither") && !source.getMagic() && !source.isExplosive();
    }

    @Override
    public void takeKnockback(float strength, double ratioX, double ratioZ) {
        super.takeKnockback(this.isBurrowed() ? 0 : strength / 4, ratioX, ratioZ);
    }

    @Override
    public boolean isPushable() {
        return !this.isInvulnerable() && super.isPushable();
    }

    @Override
    public boolean tryAttack(Entity entityIn) {
        boolean exec = super.tryAttack(entityIn);
        if (exec && entityIn instanceof PlayerEntity) {
            PlayerEntity player = (PlayerEntity) entityIn;
            List<DefaultedList<ItemStack>> allInventories = ImmutableList.of(player.inventory.main, player.inventory.armor, player.inventory.offHand);
            List<ItemStack> enchantedItems = new ArrayList<>();
            for (DefaultedList<ItemStack> inv : allInventories) {
                enchantedItems.addAll(inv.stream().filter((item) -> !EnchantmentHelper.get(item).isEmpty()).collect(Collectors.toList()));
            }
            enchantedItems.removeIf((item) -> item.getItem() instanceof AirBlockItem);
            if (!enchantedItems.isEmpty()) {
                ItemStack item = enchantedItems.get(this.random.nextInt(enchantedItems.size()));
                Map<Enchantment, Integer> enchantments = EnchantmentHelper.get(item);
                item.removeSubTag("Enchantments");
                item.removeSubTag("StoredEnchantments");
                Object[] enchants = enchantments.keySet().toArray();
                Enchantment enchant = (Enchantment) enchants[this.random.nextInt(enchants.length)];
                if (enchantments.get(enchant) > 1) {
                    enchantments.put(enchant, enchantments.get(enchant) - 1);
                } else {
                    enchantments.remove(enchant);
                }
                if (enchantments.isEmpty() && item.getItem() instanceof EnchantedBookItem) {
                    for (DefaultedList<ItemStack> inv : allInventories) {
                        if (inv.contains(item)) {
                            inv.set(inv.indexOf(item), new ItemStack(Items.BOOK));
                        }
                    }
                } else {
                    EnchantmentHelper.set(enchantments, item);
                }
            }
        }
        return exec;
    }

    /**
     * Called frequently so the entity can update its state every tick as required. For example, zombies and skeletons
     * use this to react to sunlight and start to burn.
     */
    public void tickMovement() {
        if (this.isAlive()) {
            this.setInvulnerable(this.isBurrowed());
            this.setSilent(this.isBurrowed());
        }
        super.tickMovement();
    }

    static class BiteGoal extends MeleeAttackGoal {
        private final HungerEntity hunger;

        public BiteGoal(HungerEntity entityIn, double speedIn, boolean useMemory) {
            super(entityIn, speedIn, useMemory);
            this.hunger = entityIn;
        }

        public boolean canStart() {
            this.hunger.setAttacking(this.hunger.getTarget() != null && !this.hunger.isBurrowed());
            return super.canStart() && this.hunger.world.getDifficulty() != Difficulty.PEACEFUL && !this.hunger.isBurrowed();
        }

        public boolean shouldContinue() {
            return super.shouldContinue() && this.hunger.world.getDifficulty() != Difficulty.PEACEFUL && !this.hunger.isBurrowed();
        }

        public void start() {
            super.start();
            this.hunger.setAttacking(true);
        }

        public void stop() {
            super.stop();
            this.hunger.setAttacking(this.mob.getTarget() != null);
        }
    }

    /* Determines whether a 3x3 section of ground below is suitable for burrowing */
    public boolean isSuitable(HungerEntity hungerIn, @Nullable BlockPos pos) {
        if (this.getStatusEffect(StatusEffects.WITHER) != null) return false;
        World world = hungerIn.world;
        double posX;
        double posY;
        double posZ;
        if (pos == null) {
            posX = hungerIn.getX();
            posY = hungerIn.getY();
            posZ = hungerIn.getZ();
        } else {
            posX = pos.getX();
            posY = pos.getY();
            posZ = pos.getZ();
        }
        boolean ret = true;
        for (double k = posX - 1; k <= posX + 1; ++k) {
            if (ret) {
                for (double l = posZ - 1; l <= posZ + 1; ++l) {
                    BlockState block = world.getBlockState(new BlockPos(k, posY - 1, l));
                    if (block.isIn(ModTags.Blocks.HUNGER_CAN_BURROW) && !hungerIn.isTouchingWater()) {
                        if (ret) {
                            ret = !hungerIn.isLeashed();
                        }
                    } else {
                        ret = false;
                        break;
                    }
                }
            } else {
                break;
            }
        }
        return ret;
    }

    static class FindSpotGoal extends Goal {
        protected final HungerEntity hunger;
        private double spotX;
        private double spotY;
        private double spotZ;
        private final double movementSpeed;

        public FindSpotGoal(HungerEntity theCreatureIn, double movementSpeedIn) {
            this.hunger = theCreatureIn;
            this.movementSpeed = movementSpeedIn;
            this.setControls(EnumSet.of(Goal.Control.MOVE));
        }

        public boolean canStart() {
            return this.isPossibleSpot() && this.hunger.getTarget() == null && !this.hunger.isBurrowed() && !this.hunger.isSuitable(this.hunger, null);
        }

        protected boolean isPossibleSpot() {
            net.minecraft.util.math.Vec3d vector3d = this.findPossibleSpot();
            if (vector3d == null) {
                return false;
            } else {
                this.spotX = vector3d.x;
                this.spotY = vector3d.y;
                this.spotZ = vector3d.z;
                return true;
            }
        }

        public boolean shouldContinue() {
            return !this.hunger.getNavigation().isIdle() && !this.hunger.isBurrowed();
        }

        public void start() {
            this.hunger.getNavigation().startMovingTo(this.spotX, this.spotY, this.spotZ, this.movementSpeed);
        }

        public void stop() {
            this.hunger.getNavigation().stop();
        }

        @Nullable
        protected net.minecraft.util.math.Vec3d findPossibleSpot() {
            Random random = this.hunger.getRandom();
            BlockPos blockpos = this.hunger.getBlockPos();

            for (int i = 0; i < 10; ++i) {
                BlockPos blockpos1 = blockpos.add(random.nextInt(20) - 10, random.nextInt(3) - 1, random.nextInt(20) - 10);
                if (this.hunger.isSuitable(this.hunger, blockpos1) && this.hunger.getPathfindingFavor(blockpos1) < 0.0F) {
                    return net.minecraft.util.math.Vec3d.ofBottomCenter(blockpos1);
                }
            }

            return null;
        }
    }

    static class WanderGoal extends WanderAroundFarGoal {
        private final HungerEntity hunger;

        public WanderGoal(HungerEntity entityIn) {
            super(entityIn, 1.0D, 0.1F);
            this.hunger = entityIn;
        }

        public boolean canStart() {
            return super.canStart() && !this.hunger.isBurrowed() && !this.hunger.isSuitable(this.hunger, null);
        }

        public boolean shouldContinue() {
            return super.shouldContinue() && !this.hunger.isBurrowed() && !this.hunger.isSuitable(this.hunger, null);
        }

        public void tick() {
            if (this.hunger.isBurrowed() || this.hunger.isSuitable(this.hunger, null))
                this.mob.getNavigation().stop();
        }
    }

    /**
     * Creates a vector based on caclulated direction of one of the 8 cardinal directions the entity is facing
     *
     * @return
     */
    private net.minecraft.util.math.Vec3d directionVector() {
        net.minecraft.util.math.Vec3d vec3d = net.minecraft.util.math.Vec3d.ZERO;
        double rotation = this.yaw - 180;
        if (rotation < 0) rotation += 360;
        int ordinal = MathHelper.floor(rotation / 45.0D + 0.5D) & 7;
        for (Direction direction : EightWayDirection.values()[ordinal].getDirections()) {
            vec3d = vec3d.add(direction.getOffsetX(), direction.getOffsetY(), direction.getOffsetZ());
        }
        return vec3d;
    }

    static class BurrowGoal extends Goal {
        private final HungerEntity hunger;
        private int tick = 0;
        private ItemStack cacheitem = ItemStack.EMPTY;

        public BurrowGoal(HungerEntity entityIn) {
            this.hunger = entityIn;
        }

        public boolean canStart() {
            return !this.hunger.isAttacking() && this.hunger.isSuitable(this.hunger, null);
        }

        public boolean shouldContinue() {
            return !this.hunger.isAttacking() && this.hunger.isSuitable(this.hunger, null);
        }

        public void start() {
            this.hunger.setBurrowed(true);
        }

        public void stop() {
            this.tick = 0;
            this.hunger.setBurrowed(false);
        }

        public void tick() {
            net.minecraft.util.math.Vec3d vec3d = this.hunger.directionVector().multiply(0.6D);
            Box boundingBox = this.hunger.getBoundingBox().stretch(vec3d).stretch(vec3d.multiply(-1.0D));
            List<Entity> entities = this.hunger.world.getOtherEntities(this.hunger, boundingBox);
            if (!entities.isEmpty()) {
                if (!this.hunger.isAttacking() && !this.hunger.isEnchanting()) {
                    for (Entity entity : entities) {
                        if (boundingBox.contains(entity.getPos())) {
                            if (entity instanceof ItemEntity) {
                                ItemStack item = ((ItemEntity) entity).getStack();
                                if (((ItemEntity) entity).getThrower() != this.hunger.getUuid()) {
                                    this.cacheitem = item.copy();
                                    this.hunger.world.playSound(null, this.hunger.getX(), this.hunger.getY(), this.hunger.getZ(), ModSounds.HUNGER_EAT.get(), this.hunger.getSoundCategory(), 0.8F, 0.9F);
                                    entity.remove();
                                    this.hunger.setEnchanting(true);
                                    break;
                                }
                            } else if (entity instanceof LivingEntity) {
                                if (entity.isAlive() && this.hunger.canTarget((LivingEntity) entity) && !this.hunger.isAttacking()) {
                                    this.hunger.setBurrowed(false);
                                    this.hunger.setAttacking(true);
                                    this.hunger.tryAttack(entity);
                                }
                            }
                        }
                    }
                }
            }
            if (this.hunger.isEnchanting()) {
                this.tick++;

                if (this.tick % 16 == 0) {
                    MutablePair<Integer, ItemStack> pair = this.hunger.modifyEnchantments(cacheitem, cacheitem.getDamage(), 1);
                    ItemStack item = pair.getRight();
                    if (cacheitem.getItem().equals(ModItems.VOID_HEART.get())) {
                        Explosion.DestructionType explosion$mode = this.hunger.world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING) ? Explosion.DestructionType.DESTROY : Explosion.DestructionType.NONE;
                        this.hunger.world.createExplosion(this.hunger, this.hunger.getX(), this.hunger.getY(), this.hunger.getZ(), 2.0F, explosion$mode);
                        this.hunger.remove();
                    }
                    if (pair.getLeft() == 0) {
                        if (item != ItemStack.EMPTY) {
                            this.hunger.world.playSound(null, this.hunger.getX(), this.hunger.getY(), this.hunger.getZ(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, this.hunger.getSoundCategory(), 0.8F, 0.6F);
                        }
                    } else if (pair.getLeft() == 1) {
                        this.hunger.world.playSound(null, this.hunger.getX(), this.hunger.getY(), this.hunger.getZ(), ModSounds.HUNGER_SPIT.get(), this.hunger.getSoundCategory(), 0.8F, 0.8F);
                        ItemEntity newitem = new ItemEntity(this.hunger.world, this.hunger.getX(), this.hunger.getY(), this.hunger.getZ(), cacheitem);
                        newitem.setThrower(this.hunger.getUuid());
                        this.hunger.world.spawnEntity(newitem);
                    } else {
                        this.hunger.world.playSound(null, this.hunger.getX(), this.hunger.getY(), this.hunger.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, this.hunger.getSoundCategory(), 0.8F, 0.6F);
                        item.getOrCreateTag().putInt("Bitten", 1);
                        ItemEntity newitem = new ItemEntity(this.hunger.world, this.hunger.getX(), this.hunger.getY(), this.hunger.getZ(), item);
                        newitem.setThrower(this.hunger.getUuid());
                        this.hunger.world.spawnEntity(newitem);
                    }
                    this.cacheitem = ItemStack.EMPTY;
                    this.tick = 0;
                    this.hunger.setEnchanting(false);
                }
            }
        }
    }

    static class LookGoal extends LookAroundGoal {
        private final HungerEntity hunger;

        public LookGoal(HungerEntity entitylivingIn) {
            super(entitylivingIn);
            this.hunger = entitylivingIn;
        }

        public boolean canStart() {
            return super.canStart() && !this.hunger.isBurrowed();
        }

        public boolean shouldContinue() {
            return super.shouldContinue() && !this.hunger.isBurrowed();
        }
    }

    private AnimationFactory factory = new AnimationFactory(this);

    private <E extends IAnimatable> PlayState predicate(AnimationEvent<E> event) {
        String animname = event.getController().getCurrentAnimation() != null ? event.getController().getCurrentAnimation().animationName : "";
        if (this.isBurrowed()) {
            if (this.isEnchanting()) {
                event.getController().setAnimation(new AnimationBuilder().addAnimation("bite").addAnimation("biteloop", true));
            } else {
                if (event.getController().getCurrentAnimation() != null) {
                    if (animname.equals("idle") || animname.equals("attacking") || animname.equals("chomp") || animname.equals("burrow")) {
                        event.getController().setAnimation(new AnimationBuilder().addAnimation("burrow").addAnimation("burrowed", true));
                    } else {
                        event.getController().setAnimation(new AnimationBuilder().addAnimation("burrowed", true));
                    }
                } else {
                    event.getController().setAnimation(new AnimationBuilder().addAnimation("burrow").addAnimation("burrowed", true));
                }
            }
        } else {
            if (event.getController().getCurrentAnimation() == null || animname.equals("idle") || animname.equals("attacking")) {
                if (this.isAttacking()) {
                    event.getController().setAnimation(new AnimationBuilder().addAnimation("attacking"));
                } else {
                    event.getController().setAnimation(new AnimationBuilder().addAnimation("idle"));
                }
            } else {
                if (this.isAttacking()) {
                    event.getController().setAnimation(new AnimationBuilder().addAnimation("chomp").addAnimation("attacking"));
                } else {
                    event.getController().setAnimation(new AnimationBuilder().addAnimation("chomp").addAnimation("idle"));
                }
            }
        }
        return PlayState.CONTINUE;
    }

    private <E extends IAnimatable> void soundListener(SoundKeyframeEvent<E> event) {
        if (event.sound.equals("chomp")) {
            world.playSound(this.getX(), this.getY(), this.getZ(), ModSounds.HUNGER_BITE.get(), this.getSoundCategory(), 1.0F, 1.0F, false);
        } else if (event.sound.equals("dig")) {
            BlockState block = world.getBlockState(new BlockPos(this.getX(), this.getY() - 0.5D, this.getZ()));
            if (block.isIn(BlockTags.SAND)) {
                world.playSound(this.getX(), this.getY(), this.getZ(), ModSounds.HUNGER_DIG_SAND.get(), SoundCategory.BLOCKS, 1.0F, 1.0F, false);
            } else {
                world.playSound(this.getX(), this.getY(), this.getZ(), ModSounds.HUNGER_DIG.get(), SoundCategory.BLOCKS, 1.0F, 1.0F, false);
            }
        }
    }

    private <E extends IAnimatable> void particleListener(ParticleKeyFrameEvent<E> event) {
        if (event.effect.equals("dig")) {
            for (int i = 0; i < 2; ++i) {
                BlockPos blockpos = new BlockPos(this.getX(), this.getY() - 0.5D, this.getZ());
                BlockState blockstate = this.world.getBlockState(blockpos);
                this.world.addParticle(new BlockStateParticleEffect(ParticleTypes.BLOCK, blockstate), this.getParticleX(0.5D), this.getBodyY(0), this.getParticleZ(0.5D), (this.random.nextDouble() - 0.5D) * 2.0D, this.random.nextDouble(), (this.random.nextDouble() - 0.5D) * 2.0D);
            }
        }
    }

    @Override
    public void registerControllers(AnimationData data) {
        AnimationController controller = new AnimationController(this, "controller", 2, this::predicate);
        controller.registerSoundListener(this::soundListener);
        controller.registerParticleListener(this::particleListener);
        data.addAnimationController(controller);
    }

    @Override
    public AnimationFactory getFactory() {
        return this.factory;
    }
}
