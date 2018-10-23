package protocolsupportpocketstuff;

import jdk.nashorn.internal.objects.annotations.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import protocolsupport.api.Connection;
import protocolsupport.api.ProtocolSupportAPI;
import protocolsupport.api.ProtocolVersion;
import protocolsupport.api.ServerPlatformIdentifier;
import protocolsupport.api.events.ConnectionHandshakeEvent;
import protocolsupport.api.events.ConnectionOpenEvent;
import protocolsupport.api.unsafe.pemetadata.PEMetaProviderSPI;
import protocolsupport.api.unsafe.peskins.PESkinsProviderSPI;
import protocolsupportpocketstuff.api.PocketStuffAPI;
import protocolsupportpocketstuff.api.util.PocketCon;
import protocolsupportpocketstuff.commands.CommandHandler;
import protocolsupportpocketstuff.hacks.bossbars.BossBarPacketListener;
import protocolsupportpocketstuff.hacks.holograms.HologramsPacketListener;
import protocolsupportpocketstuff.hacks.itemframes.ItemFramesPacketListener;
import protocolsupportpocketstuff.hacks.skulls.SkullTilePacketListener;
import protocolsupportpocketstuff.hacks.teams.TeamsPacketListener;
import protocolsupportpocketstuff.metadata.MetadataProvider;
import protocolsupportpocketstuff.packet.handshake.ClientLoginPacket;
import protocolsupportpocketstuff.packet.play.BlockPickRequestPacket;
import protocolsupportpocketstuff.packet.play.ModalResponsePacket;
import protocolsupportpocketstuff.packet.play.ServerSettingsRequestPacket;
import protocolsupportpocketstuff.packet.play.SkinPacket;
import protocolsupportpocketstuff.resourcepacks.ResourcePackManager;
import protocolsupportpocketstuff.skin.PcToPeProvider;
import protocolsupportpocketstuff.skin.SkinListener;
import protocolsupportpocketstuff.storage.Skins;
import protocolsupportpocketstuff.util.ActionButton;
import protocolsupportpocketstuff.util.ResourcePackListener;

import java.io.File;

public class ProtocolSupportPocketStuff extends JavaPlugin implements Listener {
	public static final String PREFIX = "[" + ChatColor.DARK_PURPLE + "PSPS" + ChatColor.RESET + "] ";
	private static ProtocolSupportPocketStuff INSTANCE;
	public static ServerPlatformIdentifier platform = ServerPlatformIdentifier.SPIGOT; // TODO: Add platform checker
	public static ProtocolSupportPocketStuff getInstance() {
		return INSTANCE;
	}

	private final ActionButton actionButton = new ActionButton();

	public ActionButton getActionButton() {
		return actionButton;
	}

	@Override
	public void onEnable() {
		INSTANCE = this;

		getCommand("protocolsupportpocketstuff").setExecutor(new CommandHandler());

		// = Config = \\
		saveDefaultConfig();

		new File(this.getDataFolder(), ResourcePackManager.FOLDER_NAME + "/").mkdirs();

		ResourcePackManager resourcePackManager = new ResourcePackManager(this);
		resourcePackManager.reloadPacks();
		PocketStuffAPI.setResourcePackManager(resourcePackManager);

		// = Events = \\
		PluginManager pm = getServer().getPluginManager();
		pm.registerEvents(this, this);
		if(getConfig().getBoolean("skins.PCtoPE")) { pm.registerEvents(new SkinListener(this), this); }

		// = SPI = \\
		if(getConfig().getBoolean("skins.PCtoPE")) { PESkinsProviderSPI.setProvider(new PcToPeProvider(this)); }
//		PEMetaProviderSPI.setProvider(new MetadataProvider());

		// = Cache = \\
		Skins.INSTANCE.buildCache(getConfig().getInt("skins.cache-size"), getConfig().getInt("skins.cache-rate"));

		if (getConfig().getBoolean("hacks.itemframes")) {
			Bukkit.getPluginManager().registerEvents(new ItemFramesPacketListener.UpdateExecutor(this), this);
		}

		pm("Hello world! :D");
	}

	@EventHandler
	public void onConnectionOpen(ConnectionOpenEvent e) {
		Connection con = e.getConnection();
		// We can't check if it is a PE player yet because it is too early to figure out
		con.addPacketListener(new ClientLoginPacket().new decodeHandler(this, con));
	}

	@EventHandler
	public void onConnectionHandshake(ConnectionHandshakeEvent e) {
		Connection con = e.getConnection();
		if(PocketCon.isPocketConnection(con)) {

			// = Packet Listeners = \\
			con.addPacketListener(new ModalResponsePacket().new decodeHandler(this, con));

			if (!PocketStuffAPI.getResourcePackManager().getBehaviorPacks().isEmpty() || !PocketStuffAPI.getResourcePackManager().getResourcePacks().isEmpty())
				con.addPacketListener(new ResourcePackListener(this, con));

			if (getConfig().getBoolean("skins.PEtoPC")) { con.addPacketListener(new SkinPacket().new decodeHandler(this, con)); }
			if (getConfig().getBoolean("hacks.middleclick")) { con.addPacketListener(new BlockPickRequestPacket().new decodeHandler(this, con)); }
			if (getConfig().getBoolean("hacks.holograms")) { con.addPacketListener(new HologramsPacketListener(con)); }
			if (getConfig().getBoolean("hacks.player-heads-skins.skull-blocks")) { con.addPacketListener(new SkullTilePacketListener(this, con)); }
			if (platform == ServerPlatformIdentifier.SPIGOT) { // Spigot only hacks
				if (getConfig().getBoolean("hacks.teams")) {
					con.addPacketListener(new TeamsPacketListener(con));
				}
				if (getConfig().getBoolean("hacks.itemframes")) {
					con.addPacketListener(new ItemFramesPacketListener(this, con));
				}
				if (getConfig().getBoolean("hacks.bossbars")) {
					con.addPacketListener(new BossBarPacketListener(con));
				}
			}

			con.addPacketListener(new ServerSettingsRequestPacket().new decodeHandler(this, con));
		}
	}

	@EventHandler
	public void onWorld(PlayerChangedWorldEvent event) { // Magic code to compatible bungee transport
		Connection conn = ProtocolSupportAPI.getConnection(event.getPlayer());
		if (conn.getVersion() != ProtocolVersion.MINECRAFT_PE) {
			return;
		}

		HologramsPacketListener hologram = HologramsPacketListener.get(conn);
		if (hologram != null) {
			hologram.clean();
		}

		SkullTilePacketListener skull = SkullTilePacketListener.get(conn);
		if (skull != null) {
			skull.clean();
		}

		ItemFramesPacketListener frame = (ItemFramesPacketListener) conn.getMetadata(ItemFramesPacketListener.META_KEY);
		if (frame != null) {
			frame.clean();
		}
	}

	@Override
	public void onDisable() {
		pm("Bye world :O");
	}

	/**
	 * Sends a plugin message.
	 * @param msg
	 */
	public void pm(String msg) {
		msg = "[" + ChatColor.DARK_PURPLE + "PSPS" + ChatColor.RESET + "] " + msg;
		if (getConfig().getBoolean("logging.disable-colors", false)) {
			msg = ChatColor.stripColor(msg);
		}
		getServer().getConsoleSender().sendMessage(msg);
	}

	/**
	 * Sends a debug plugin message.
	 * @param msg
	 */
	public void debug(String msg) {
		if (!getConfig().getBoolean("logging.enable-debug", false)) { return; }
		msg = "[" + ChatColor.RED + "PSPS" + ChatColor.RESET + "] " + msg;
		if (getConfig().getBoolean("logging.disable-colors", false)) {
			msg = ChatColor.stripColor(msg);
		}
		getServer().getConsoleSender().sendMessage(msg);
	}
}
