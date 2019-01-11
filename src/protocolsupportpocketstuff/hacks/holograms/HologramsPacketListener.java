package protocolsupportpocketstuff.hacks.holograms;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import protocolsupport.api.Connection;
import protocolsupport.api.ProtocolVersion;
import protocolsupport.protocol.packet.middleimpl.clientbound.play.v_pe.EntityMetadata;
import protocolsupport.protocol.packet.middleimpl.clientbound.play.v_pe.SetPosition;
import protocolsupport.protocol.pipeline.version.v_pe.PEPacketDecoder;
import protocolsupport.protocol.serializer.StringSerializer;
import protocolsupport.protocol.serializer.VarInt;
import protocolsupport.protocol.serializer.VarNumberSerializer;
import protocolsupport.protocol.typeremapper.pe.PEMetadataFlags;
import protocolsupport.protocol.typeremapper.pe.PEPacketIDs;
import protocolsupport.protocol.utils.datawatcher.DataWatcherObject;
import protocolsupport.protocol.utils.datawatcher.ReadableDataWatcherObject;
import protocolsupport.protocol.utils.datawatcher.objects.DataWatcherObjectByte;
import protocolsupport.protocol.utils.datawatcher.objects.DataWatcherObjectFloatLe;
import protocolsupport.protocol.utils.datawatcher.objects.DataWatcherObjectItemStack;
import protocolsupport.protocol.utils.datawatcher.objects.DataWatcherObjectPosition;
import protocolsupport.protocol.utils.datawatcher.objects.DataWatcherObjectString;
import protocolsupport.protocol.utils.datawatcher.objects.DataWatcherObjectVarInt;
import protocolsupport.protocol.utils.i18n.I18NData;
import protocolsupport.utils.CollectionsUtils;
import protocolsupportpocketstuff.api.util.PocketCon;
import protocolsupportpocketstuff.packet.play.EntityDestroyPacket;
import protocolsupportpocketstuff.packet.play.PlayerMovePacket;
import protocolsupportpocketstuff.packet.play.SpawnPlayerPacket;

import java.util.HashMap;
import java.util.UUID;

public class HologramsPacketListener extends Connection.PacketListener {
    private static final HashMap<Integer, ReadableDataWatcherObject<?>> DATA_WATCHERS = new HashMap<>();
    private static final int ARMOR_STAND_ID = 61;
    private static final double Y_OFFSET = 1.6200000047683716D;

    static {
        DATA_WATCHERS.put(0, new DataWatcherObjectByte());
        DATA_WATCHERS.put(1, new ReadableDataWatcherObject<Short>() {
            public void readFromStream(ByteBuf byteBuf, ProtocolVersion protocolVersion, String s) throws DecoderException {
                value = byteBuf.readShortLE();
            }

            public void writeToStream(ByteBuf byteBuf, ProtocolVersion protocolVersion, String s) {
                byteBuf.writeShortLE(value);
            }
        });
        DATA_WATCHERS.put(2, new DataWatcherObjectVarInt());
        DATA_WATCHERS.put(3, new ReadableDataWatcherObject<Float>() {
            public void readFromStream(ByteBuf byteBuf, ProtocolVersion protocolVersion, String s) throws DecoderException {
                value = byteBuf.readFloatLE();
            }

            public void writeToStream(ByteBuf byteBuf, ProtocolVersion protocolVersion, String s) {
                byteBuf.writeFloatLE(value);
            }
        });
        DATA_WATCHERS.put(4, new DataWatcherObjectString());
        DATA_WATCHERS.put(5, new DataWatcherObjectItemStack());
        DATA_WATCHERS.put(6, new DataWatcherObjectPosition());
        DATA_WATCHERS.put(7, new ReadableDataWatcherObject<Long>() {
            public void readFromStream(ByteBuf byteBuf, ProtocolVersion protocolVersion, String s) throws DecoderException {
                value = VarNumberSerializer.readSVarLong(byteBuf);
            }

            public void writeToStream(ByteBuf byteBuf, ProtocolVersion protocolVersion, String s) {
                VarNumberSerializer.writeSVarLong(byteBuf, value);
            }
        });
        DATA_WATCHERS.put(8, new DataWatcherObjectPosition());
    }

    private Connection con;
    private HashMap<Long, CachedArmorStand> cachedArmorStands = new HashMap<>();

    public HologramsPacketListener(Connection con) {
        this.con = con;
    }

    @Override
    public void onRawPacketSending(RawPacketEvent event) {
        ByteBuf data = event.getData();
        ProtocolVersion version = con.getVersion();
        int packetId = PEPacketDecoder.sReadPacketId(version, data);

        if (packetId == PEPacketIDs.MOVE_ENTITY_ABSOLUTE) {
            long entityId = VarInt.readUnsignedVarLong(data);

            if (!cachedArmorStands.containsKey(entityId))
                return;

            CachedArmorStand armorStand = cachedArmorStands.get(entityId);

            byte flag = data.readByte();
            boolean onGround = (flag & 128) == 128;
            boolean teleported = (flag & 64) == 64;
            armorStand.x = data.readFloatLE();
            armorStand.y = data.readFloatLE() + (float) Y_OFFSET;
            armorStand.z = data.readFloatLE();
            if (armorStand.isHologram()) {
                int pitch = data.readByte();
                int yaw = data.readByte();
                int headYaw = data.readByte();
                event.setData(new PlayerMovePacket(armorStand.getEntityId(), armorStand.x, armorStand.y, armorStand.z, pitch, headYaw, yaw, teleported ? SetPosition.ANIMATION_MODE_TELEPORT : SetPosition.ANIMATION_MODE_ALL, onGround).encode(con));
            }
            return;
        }
        if (packetId == PEPacketIDs.SPAWN_ENTITY) {
            VarNumberSerializer.readSVarLong(data);// unique id
            long entityId = VarNumberSerializer.readVarLong(data); // runtime id
            if (version.isBefore(ProtocolVersion.MINECRAFT_PE_1_8)) {
                int typeId = VarNumberSerializer.readVarInt(data);
                if (typeId != ARMOR_STAND_ID)
                    return;
            } else {
                String entityType = StringSerializer.readString(data, version);
                if (!entityType.equals("minecraft:armor_stand"))
                    return;
            }

            if (cachedArmorStands.containsKey(entityId))
                return;

            float x = data.readFloatLE();
            float y = data.readFloatLE();
            float z = data.readFloatLE();

            data.readFloatLE(); // motx
            data.readFloatLE(); // moty
            data.readFloatLE(); // motz

            data.readFloatLE(); // pitch
            data.readFloatLE(); // yaw
            data.readFloatLE();// head yaw

            int len = VarNumberSerializer.readVarInt(data);// attribute length, unused
            for (int i = 0; i < len; i++) {
                StringSerializer.readString(data, ProtocolVersion.MINECRAFT_PE);
                data.readFloatLE();
                data.readFloatLE();
                data.readFloatLE();
            }

            CachedArmorStand armorStand = new CachedArmorStand(getHologramId(entityId), x, y, z);
            cachedArmorStands.put(entityId, armorStand);

            String hologramName = retrieveHologramName(data);

            if (hologramName == null)
                return;

            event.setCancelled(true);

            armorStand.nametag = hologramName;
            armorStand.setHologram(true);

            // omg it is an hologram :O
            armorStand.spawnHologram(entityId, this);
            return;
        }
        if (packetId == PEPacketIDs.SET_ENTITY_DATA) {
            long entityId = VarNumberSerializer.readVarLong(data);

            if (!cachedArmorStands.containsKey(entityId))
                return;

            String hologramName = retrieveHologramName(data);

            if (hologramName == null)
                return;

            // omg it is an hologram :O
            CachedArmorStand armorStand = cachedArmorStands.get(entityId);

            if (armorStand.isHologram()) {
                event.setData(new EntityDestroyPacket(armorStand.getEntityId()).encode(con));
            } else {
                event.setData(new EntityDestroyPacket(entityId).encode(con));
            }

            armorStand.nametag = hologramName;
            armorStand.setHologram(true);

            armorStand.spawnHologram(entityId, this);
            return;
        }
        if (packetId == PEPacketIDs.ENTITY_DESTROY) {
            long entityId = VarNumberSerializer.readSVarLong(data);
            CachedArmorStand stand = cachedArmorStands.remove(entityId);
            if (stand != null && stand.isHologram()) {
                event.setData(new EntityDestroyPacket(stand.getEntityId()).encode(con));
            }
        }
    }

    private String retrieveHologramName(ByteBuf data) {
        boolean hasCustomName = false;
        boolean invisible = false;
        boolean shownametag = false;
        //boolean alwaysShowNametag = false;
        //boolean showBase = false;
        String nametag = null;

        int length = VarNumberSerializer.readVarInt(data);
        ProtocolVersion version = con.getVersion();

        for (int idx = 0; length > idx; idx++) {
            int metaKey = VarNumberSerializer.readVarInt(data);
            int metaType = VarNumberSerializer.readVarInt(data) % 8;

//            System.out.println("!!! meta type " + metaType);
//            System.out.println("!!! meta key " + metaKey);

            ReadableDataWatcherObject<?> dw = DATA_WATCHERS.get(metaType);

            dw.readFromStream(data, version, I18NData.DEFAULT_LOCALE);

            if (metaKey == 4) {
                nametag = (String) dw.getValue();
                hasCustomName = !nametag.isEmpty();
                if (nametag.equals("__null")) {
                    nametag = "";
                }
            }

            if (metaKey == 0) {
                long peBaseFlags = ((Number) dw.getValue()).longValue();
//                System.out.println("!!! meta ctx " + Long.toBinaryString(peBaseFlags));
                invisible = ((peBaseFlags >> (PEMetadataFlags.INVISIBLE.getFlag(version) - 1)) & 1) == 1;
                shownametag = ((peBaseFlags >> (PEMetadataFlags.CAN_SHOW_NAME.getFlag(version) - 1)) & 1) == 1;
//                System.out.println(String.format("!!! invisible=%s shownametag=%s", invisible, shownametag));
            }
        }

        return hasCustomName && invisible && shownametag ? nametag : null;
    }

    public static long getHologramId(long entityId) {
        if (Long.compareUnsigned(entityId, Integer.MAX_VALUE) == -1) {
            return entityId + Integer.MAX_VALUE;
        }
        return entityId;
    }

    static class CachedArmorStand {
        private long entityId;
        private float x;
        private float y;
        private float z;
        private String nametag;
        private boolean hologram;

        CachedArmorStand(long entityId, float x, float y, float z) {
            this.entityId = entityId;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public void spawnHologram(long originId, HologramsPacketListener listener) {
            CollectionsUtils.ArrayMap<DataWatcherObject<?>> metadata = new CollectionsUtils.ArrayMap<>(EntityMetadata.PeMetaBase.BOUNDINGBOX_HEIGTH + 1);
//            long peBaseFlags = entity.getDataCache().getPeBaseFlags();
//            System.out.println("!!! sent new meta ctx " + Long.toBinaryString(peBaseFlags));
//            metadata.put(EntityMetadata.PeMetaBase.FLAGS, new DataWatcherObjectSVarLong(peBaseFlags));
            metadata.put(EntityMetadata.PeMetaBase.NAMETAG, new DataWatcherObjectString(nametag));
			metadata.put(EntityMetadata.PeMetaBase.SCALE, new DataWatcherObjectFloatLe(0.001f)); // scale
			metadata.put(EntityMetadata.PeMetaBase.BOUNDINGBOX_WIDTH, new DataWatcherObjectFloatLe(0.001f)); // bb width
			metadata.put(EntityMetadata.PeMetaBase.BOUNDINGBOX_HEIGTH, new DataWatcherObjectFloatLe(0.001f)); // bb height

            SpawnPlayerPacket packet = new SpawnPlayerPacket(
                    new UUID(0x80, originId),
                    nametag,
                    entityId,
                    x, y, z, // coordinates
                    0, 0, 0, // motion
                    0, 0, 0, // pitch, head yaw & yaw
                    metadata
            );

            PocketCon.sendPocketPacket(listener.con, packet);
        }

        public long getEntityId() {
            return entityId;
        }

        public boolean isHologram() {
            return hologram;
        }

        public void setHologram(boolean isHologram) {
            this.hologram = isHologram;
        }
    }
}
