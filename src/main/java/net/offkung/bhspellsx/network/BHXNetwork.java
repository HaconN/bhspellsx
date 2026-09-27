package net.offkung.bhspellsx.network;

import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.offkung.bhspellsx.entity.spells.jing_guang_pan.JingGuangPanManager;

/** Direction-checked messages. Client callbacks are installed only by client setup. */
public final class BHXNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("bhspellsx", "jing_guang_pan"), () -> "6", "6"::equals, "6"::equals);
    public record WavePath(UUID id, ResourceLocation dimension, net.minecraft.world.phys.Vec3 origin,
            net.minecraft.world.phys.Vec3 direction, float yaw, boolean left, double rollDegrees, double distance, boolean done) {}
    public record Halo(UUID player, ResourceLocation dimension, long revision, long age, boolean active, boolean clear) {}
    public static Consumer<Halo> clientHalo = packet -> {};
    public static void halo(ServerPlayer player, Halo packet) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), packet);
    }
    public static void haloTo(ServerPlayer viewer, Halo packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), packet);
    }
    public static Consumer<WavePath> clientWave = packet -> {};
    public record State(UUID session, boolean active, boolean gliding) {}
    public record Attack(UUID session, long sequence, boolean left) {}
    public record Animation(int entityId, boolean left) {}
    public record Rejected(UUID session, long sequence, int retryTicks) {}
    public record Boost(UUID session, double anchorY, boolean stop) {}
    public static Consumer<Rejected> clientRejected = packet -> {};
    public static Consumer<Boost> clientBoost = packet -> {};
    public record Bindings(UUID session, int leftMask) {}
    public static Consumer<State> clientState = packet -> {};
    public static Consumer<Animation> clientAnimation = packet -> {};
    public static void register() {
        CHANNEL.messageBuilder(Halo.class, 7, NetworkDirection.PLAY_TO_CLIENT)
            .encoder((p,b) -> { b.writeUUID(p.player()); b.writeResourceLocation(p.dimension());
                b.writeLong(p.revision()); b.writeLong(p.age()); b.writeBoolean(p.active()); b.writeBoolean(p.clear()); })
            .decoder(b -> new Halo(b.readUUID(),b.readResourceLocation(),b.readLong(),b.readLong(),b.readBoolean(),b.readBoolean()))
            .consumerMainThread((p,c) -> clientHalo.accept(p)).add();
        CHANNEL.messageBuilder(State.class, 0, NetworkDirection.PLAY_TO_CLIENT)
            .encoder((p, b) -> { b.writeUUID(p.session()); b.writeBoolean(p.active()); b.writeBoolean(p.gliding()); })
            .decoder(b -> new State(b.readUUID(), b.readBoolean(), b.readBoolean()))
            .consumerMainThread((p, c) -> clientState.accept(p)).add();
        CHANNEL.messageBuilder(Attack.class, 1, NetworkDirection.PLAY_TO_SERVER)
            .encoder((p, b) -> { b.writeUUID(p.session()); b.writeLong(p.sequence()); b.writeBoolean(p.left()); })
            .decoder(b -> new Attack(b.readUUID(), b.readLong(), b.readBoolean()))
            .consumerMainThread((p, c) -> {
                ServerPlayer player = c.get().getSender();
                if (player != null) JingGuangPanManager.request(player, p);
            }).add();
        CHANNEL.messageBuilder(Animation.class, 2, NetworkDirection.PLAY_TO_CLIENT)
            .encoder((p, b) -> { b.writeInt(p.entityId()); b.writeBoolean(p.left()); })
            .decoder(b -> new Animation(b.readInt(), b.readBoolean()))
            .consumerMainThread((p, c) -> clientAnimation.accept(p)).add();
        CHANNEL.messageBuilder(Bindings.class, 3, NetworkDirection.PLAY_TO_SERVER)
            .encoder((p, b) -> { b.writeUUID(p.session()); b.writeInt(p.leftMask()); })
            .decoder(b -> new Bindings(b.readUUID(), b.readInt()))
            .consumerMainThread((p, c) -> {
                if (c.get().getSender() != null) JingGuangPanManager.bindings(c.get().getSender(), p);
            }).add();
        CHANNEL.messageBuilder(Rejected.class, 4, NetworkDirection.PLAY_TO_CLIENT)
            .encoder((p, b) -> { b.writeUUID(p.session()); b.writeLong(p.sequence()); b.writeInt(p.retryTicks()); })
            .decoder(b -> new Rejected(b.readUUID(), b.readLong(), b.readInt()))
            .consumerMainThread((p, c) -> clientRejected.accept(p)).add();
        CHANNEL.messageBuilder(Boost.class, 5, NetworkDirection.PLAY_TO_CLIENT)
            .encoder((p, b) -> { b.writeUUID(p.session()); b.writeDouble(p.anchorY()); b.writeBoolean(p.stop()); })
            .decoder(b -> new Boost(b.readUUID(), b.readDouble(), b.readBoolean()))
            .consumerMainThread((p, c) -> clientBoost.accept(p)).add();
        CHANNEL.messageBuilder(WavePath.class, 6, NetworkDirection.PLAY_TO_CLIENT)
            .encoder((p,b) -> { b.writeUUID(p.id()); b.writeResourceLocation(p.dimension());
                b.writeDouble(p.origin().x); b.writeDouble(p.origin().y); b.writeDouble(p.origin().z);
                b.writeDouble(p.direction().x); b.writeDouble(p.direction().y); b.writeDouble(p.direction().z);
                b.writeFloat(p.yaw()); b.writeBoolean(p.left()); b.writeDouble(p.rollDegrees()); b.writeDouble(p.distance()); b.writeBoolean(p.done()); })
            .decoder(b -> new WavePath(b.readUUID(), b.readResourceLocation(),
                new net.minecraft.world.phys.Vec3(b.readDouble(),b.readDouble(),b.readDouble()),
                new net.minecraft.world.phys.Vec3(b.readDouble(),b.readDouble(),b.readDouble()),
                b.readFloat(),b.readBoolean(),b.readDouble(),b.readDouble(),b.readBoolean()))
            .consumerMainThread((p,c) -> clientWave.accept(p)).add();
    }
    public static void wave(ServerPlayer owner, WavePath packet) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> owner), packet);
    }
    public static void rejected(ServerPlayer player, Attack request, int retryTicks) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Rejected(request.session(), request.sequence(), retryTicks));
    }
    public static void boost(ServerPlayer player, UUID session, double anchorY, boolean stop) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Boost(session, anchorY, stop));
    }
    public static void state(ServerPlayer player, UUID session, boolean active, boolean gliding) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new State(session, active, gliding));
    }
    public static void attack(UUID session, long sequence, boolean left) {
        CHANNEL.sendToServer(new Attack(session, sequence, left));
    }
    public static void bindings(UUID session, int leftMask) { CHANNEL.sendToServer(new Bindings(session, leftMask)); }
    public static void animation(ServerPlayer player, boolean left) {
        // The caster already predicted this animation; never restart it on server echo.
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> player), new Animation(player.getId(), left));
    }
    private BHXNetwork() {}
}
