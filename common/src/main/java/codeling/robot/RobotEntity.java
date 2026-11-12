package codeling.robot;

import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
//? if >=1.21.6 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?} else {
/*import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
*///?}
import net.minecraft.world.phys.Vec3;

/**
 * The robot. Server authoritative; the client only renders it. No AI, no gravity, no damage, not pushable.
 * Facing is the vanilla yaw (one of four horizontal directions), which vanilla already syncs and saves as "Rotation".
 * Inventory, selected slot, the place-on-move assist, gravity mode and home are server only and saved with the entity.
 */
public class RobotEntity extends Entity {
    // shortcut: 5 s proposal for "a few seconds" (issue #80); make it a client option if players ask.
    public static final int SAY_TICKS = 100;
    /** agent.say text, synced so the renderer shows it over the robot; empty when there is none. Never saved. */
    private static final EntityDataAccessor<String> SAY = SynchedEntityData.defineId(RobotEntity.class, EntityDataSerializers.STRING);
    /** The owner's player name, synced so the renderer can show it up close (issue #151); saved for offline owners. */
    private static final EntityDataAccessor<String> OWNER_NAME = SynchedEntityData.defineId(RobotEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SKIN = SynchedEntityData.defineId(RobotEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> ANIMATION = SynchedEntityData.defineId(RobotEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Long> ANIMATION_START = SynchedEntityData.defineId(RobotEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> PRIMARY_COLOR = SynchedEntityData.defineId(RobotEntity.class, EntityDataSerializers.INT);
    private int sayTicks;
    private UUID owner;
    /** Which of its owner's robots this is (issue #140); set by RobotManager from robots.dat, null for a stale copy. */
    private RobotLinks.Key key;
    private final SimpleContainer inventory = new SimpleContainer(RobotCommand.SLOTS);
    private int selectedSlot = 1;
    private boolean placeOnMove;
    private boolean gravityMode;
    private GlobalPos home;

    public RobotEntity(EntityType<?> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public UUID owner() {
        return owner;
    }

    void setOwner(UUID owner) {
        this.owner = owner;
    }

    public RobotLinks.Key key() {
        return key;
    }

    /** The robot's name, empty for the owner's default robot. */
    public String name() {
        return key == null ? "" : key.name();
    }

    /**
     * Sets which robot this is. A named robot shows its name as a vanilla custom name (the name tag); the default
     * robot keeps whatever display name a name tag gave it.
     */
    void setKey(RobotLinks.Key key) {
        this.key = key;
        if (!key.name().isEmpty() && !Component.literal(key.name()).equals(getCustomName())) {
            setCustomName(Component.literal(key.name()));
            setCustomNameVisible(true);
        }
    }

    /** The owner's player name, or empty when it was never known. */
    public String ownerName() {
        return entityData.get(OWNER_NAME);
    }

    void setOwnerName(String name) {
        entityData.set(OWNER_NAME, name);
    }

    public String skin() {
        return entityData.get(SKIN);
    }

    public void setSkin(String skin) {
        entityData.set(SKIN, RobotAppearance.requireSkin(skin));
        rememberAppearance();
    }

    public int primaryColor() {
        return entityData.get(PRIMARY_COLOR);
    }

    public void setPrimaryColor(int color) {
        if (color < -1 || color > 0xFFFFFF) throw new IllegalArgumentException("RGB color out of range");
        entityData.set(PRIMARY_COLOR, color);
        rememberAppearance();
    }

    void rememberAppearance() {
        if (key != null && level() instanceof ServerLevel serverLevel) {
            RobotLinks links = RobotLinks.get(serverLevel.getServer());
            if (getUUID().equals(links.robot(key))) links.setAppearance(getUUID(), skin(), primaryColor());
        }
    }

    public String animation() {
        return entityData.get(ANIMATION);
    }

    public void animate(String animation) {
        entityData.set(ANIMATION, RobotAppearance.requireAnimation(animation));
        entityData.set(ANIMATION_START, level().getGameTime());
    }

    public float animationSeconds(float partialTick) {
        return Math.max(0, (level().getGameTime() - entityData.get(ANIMATION_START) + partialTick) / 20.0F);
    }

    /** 27 slots, index 0 is MakeCode slot 1. */
    public SimpleContainer inventory() {
        return inventory;
    }

    /** 1-based, as in MakeCode. */
    public int selectedSlot() {
        return selectedSlot;
    }

    void setSelectedSlot(int slot) {
        selectedSlot = slot;
    }

    public boolean placeOnMove() {
        return placeOnMove;
    }

    void setPlaceOnMove(boolean on) {
        placeOnMove = on;
    }

    /** Codeling's own fall rule, applied by the executor in RobotManager. Vanilla gravity stays off. */
    public boolean gravityMode() {
        return gravityMode;
    }

    void setGravityMode(boolean on) {
        gravityMode = on;
    }

    /** agent.setHome (issue #78): a block position with its dimension, or null when none is set. */
    public GlobalPos home() {
        return home;
    }

    void setHome(GlobalPos home) {
        this.home = home;
    }

    /** The speech bubble text, or empty. */
    public String said() {
        return entityData.get(SAY);
    }

    /** Shows {@code text} over the robot for {@link #SAY_TICKS}; a newer text replaces it and starts the time again. */
    void say(String text) {
        entityData.set(SAY, text);
        sayTicks = SAY_TICKS;
    }

    @Override
    public void tick() {
        super.tick();
        if (sayTicks > 0 && --sayTicks == 0) entityData.set(SAY, "");
    }

    // Items must not vanish: a robot that is killed or discarded drops what it still holds where it stands.
    // Unloading keeps it. RobotManager empties a robot first when its items live on elsewhere (moved to the new
    // robot by /robot spawn in another dimension, or a stale copy whose items are not its own).
    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && level() instanceof ServerLevel) Containers.dropContents(level(), this, inventory);
        super.remove(reason);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(SAY, "");
        entityData.define(OWNER_NAME, "");
        entityData.define(SKIN, "workshop");
        entityData.define(PRIMARY_COLOR, -1);
        entityData.define(ANIMATION, "idle");
        entityData.define(ANIMATION_START, 0L);
    }

    // The same keys and value formats on every version: before 1.21.6 entities save to a CompoundTag directly.
    //? if >=1.21.6 {
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
        ContainerHelper.loadAllItems(input, inventory.getItems());
        selectedSlot = Math.clamp(input.getIntOr("SelectedSlot", 1), 1, RobotCommand.SLOTS);
        placeOnMove = input.getBooleanOr("PlaceOnMove", false);
        gravityMode = input.getBooleanOr("GravityMode", false);
        home = input.read("Home", GlobalPos.CODEC).orElse(null);
        setOwnerName(input.getStringOr("OwnerName", ""));
        setSkin(RobotAppearance.savedSkin(input.getStringOr("Skin", "workshop")));
        setPrimaryColor(RobotAppearance.savedColor(input.getIntOr("PrimaryColor", -1)));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.storeNullable("Owner", UUIDUtil.CODEC, owner);
        ContainerHelper.saveAllItems(output, inventory.getItems());
        output.putInt("SelectedSlot", selectedSlot);
        output.putBoolean("PlaceOnMove", placeOnMove);
        output.putBoolean("GravityMode", gravityMode);
        output.storeNullable("Home", GlobalPos.CODEC, home);
        output.putString("OwnerName", ownerName());
        output.putString("Skin", skin());
        output.putInt("PrimaryColor", primaryColor());
    }
    //?} else if >=1.21.5 {
    /*// 1.21.5's CompoundTag has the ValueInput style getters and codec reads, and no UUID methods any more.
    @Override
    protected void readAdditionalSaveData(CompoundTag input) {
        owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
        ContainerHelper.loadAllItems(input, inventory.getItems(), registryAccess());
        selectedSlot = Math.clamp(input.getIntOr("SelectedSlot", 1), 1, RobotCommand.SLOTS);
        placeOnMove = input.getBooleanOr("PlaceOnMove", false);
        gravityMode = input.getBooleanOr("GravityMode", false);
        home = input.read("Home", GlobalPos.CODEC).orElse(null);
        setOwnerName(input.getStringOr("OwnerName", ""));
        setSkin(RobotAppearance.savedSkin(input.getStringOr("Skin", "workshop")));
        setPrimaryColor(RobotAppearance.savedColor(input.getIntOr("PrimaryColor", -1)));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag output) {
        output.storeNullable("Owner", UUIDUtil.CODEC, owner);
        ContainerHelper.saveAllItems(output, inventory.getItems(), registryAccess());
        output.putInt("SelectedSlot", selectedSlot);
        output.putBoolean("PlaceOnMove", placeOnMove);
        output.putBoolean("GravityMode", gravityMode);
        output.storeNullable("Home", GlobalPos.CODEC, home);
        output.putString("OwnerName", ownerName());
        output.putString("Skin", skin());
        output.putInt("PrimaryColor", primaryColor());
    }
    *///?} else {
    /*@Override
    protected void readAdditionalSaveData(CompoundTag input) {
        owner = input.hasUUID("Owner") ? input.getUUID("Owner") : null;
        ContainerHelper.loadAllItems(input, inventory.getItems(), registryAccess());
        selectedSlot = Math.clamp(input.contains("SelectedSlot") ? input.getInt("SelectedSlot") : 1, 1, RobotCommand.SLOTS);
        placeOnMove = input.getBoolean("PlaceOnMove");
        gravityMode = input.getBoolean("GravityMode");
        home = input.contains("Home") ? GlobalPos.CODEC.parse(NbtOps.INSTANCE, input.get("Home")).result().orElse(null) : null;
        setOwnerName(input.getString("OwnerName"));
        setSkin(RobotAppearance.savedSkin(input.getString("Skin")));
        setPrimaryColor(RobotAppearance.savedColor(input.contains("PrimaryColor") ? input.getInt("PrimaryColor") : -1));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag output) {
        if (owner != null) output.putUUID("Owner", owner);
        ContainerHelper.saveAllItems(output, inventory.getItems(), registryAccess());
        output.putInt("SelectedSlot", selectedSlot);
        output.putBoolean("PlaceOnMove", placeOnMove);
        output.putBoolean("GravityMode", gravityMode);
        if (home != null) GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, home).result().ifPresent(tag -> output.put("Home", tag));
        output.putString("OwnerName", ownerName());
        output.putString("Skin", skin());
        output.putInt("PrimaryColor", primaryColor());
    }
    *///?}

    //? if >=1.21.2 {
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }
    //?} else {
    /*@Override
    public boolean hurt(DamageSource source, float damage) {
        return false;
    }
    *///?}

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE_ENTITY;
    }

    /**
     * Right-click opens the inventory screen for the owner and ops; others get a message (issues #68 and #70). A
     * vanilla name tag item renames the robot instead (issue #151), with the same rules as /robot rename.
     */
    @Override
    //? if >=26.1 {
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
    //?} else
    /*public InteractionResult interact(Player player, InteractionHand hand) {*/
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(Items.NAME_TAG) && stack.get(DataComponents.CUSTOM_NAME) != null) {
            if (player instanceof ServerPlayer serverPlayer) RobotManager.nameTag(serverPlayer, this, stack);
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) RobotManager.openInventory(serverPlayer, this);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isPickable() {
        return true;
    }
}
