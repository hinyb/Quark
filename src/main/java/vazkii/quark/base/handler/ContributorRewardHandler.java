package vazkii.quark.base.handler;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.WeakHashMap;

import com.google.common.collect.ImmutableSet;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import vazkii.quark.base.Quark;

@Mod.EventBusSubscriber(modid = Quark.MOD_ID)
public class ContributorRewardHandler {

	private static final ImmutableSet<String> DEV_UUID = ImmutableSet.of(
			"8c826f34-113b-4238-a173-44639c53b6e6", // Vazkii
			"0d054077-a977-4b19-9df9-8a4d5bf20ec3", // wi0iv
			"458391f5-6303-4649-b416-e4c0d18f837a", // yrsegal
			"75c298f9-27c8-415b-9a16-329e3884054b", // minecraftvinnyq
			"6c175d10-198a-49f9-8e2b-c74f1f0178f3"); // MilkBringer / Sully

	private static final Set<String> done = Collections.newSetFromMap(new WeakHashMap<>());

	private static Thread thread;
	private static String name;

	private static final Map<String, Integer> tiers = new HashMap<>();

	public static int localPatronTier = 0;
	public static String featuredPatron = "N/A";

	@OnlyIn(Dist.CLIENT)
	public static void getLocalName() {
		name = Minecraft.getInstance().getUser().getName().toLowerCase(Locale.ROOT);
	}

	public static void init() {}

	public static int getTier(Player player) {
		return getTier(player.getGameProfile().getName());
	}

	public static int getTier(String name) {
		return tiers.getOrDefault(name.toLowerCase(Locale.ROOT), 0);
	}

	@SubscribeEvent
	@OnlyIn(Dist.CLIENT)
	public static void onRenderPlayer(RenderPlayerEvent.Post event) {
		Player player = event.getEntity();
		String uuid = player.getUUID().toString();
		if(player instanceof AbstractClientPlayer clientPlayer && DEV_UUID.contains(uuid) && !done.contains(uuid)) {
			if(clientPlayer.isCapeLoaded()) {
				PlayerInfo info = clientPlayer.playerInfo;
				Map<MinecraftProfileTexture.Type, ResourceLocation> textures = info.textureLocations;
				ResourceLocation loc = new ResourceLocation("quark", "textures/misc/dev_cape.png");
				textures.put(MinecraftProfileTexture.Type.CAPE, loc);
				textures.put(MinecraftProfileTexture.Type.ELYTRA, loc);
				done.add(uuid);
			}
		}
	}

	@SubscribeEvent
	@OnlyIn(Dist.DEDICATED_SERVER)
	public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
		ContributorRewardHandler.init();
	}

	private static void load(Properties props) {
		List<String> allPatrons = new ArrayList<>(props.size());

		props.forEach((k, v) -> {
			String key = (String) k;
			String value = (String) v;

			int tier = Integer.parseInt(value);
			if(tier < 10)
				allPatrons.add(key);
			tiers.put(key.toLowerCase(Locale.ROOT), tier);

			if(key.toLowerCase(Locale.ROOT).equals(name))
				localPatronTier = tier;
		});

		if(!allPatrons.isEmpty())
			featuredPatron = allPatrons.get((int) (Math.random() * allPatrons.size()));
	}

	private static class ThreadContributorListLoader extends Thread {

		public ThreadContributorListLoader() {
			setName("Quark Contributor Loading Thread");
			setDaemon(true);
			start();
		}

		@Override
		public void run() {
			try {
				URL url = new URL("https://raw.githubusercontent.com/VazkiiMods/Quark/master/contributors.properties");
				URLConnection conn = url.openConnection();
				conn.setConnectTimeout(10*1000);
				conn.setReadTimeout(10*1000);

				Properties patreonTiers = new Properties();
				try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
					patreonTiers.load(reader);
					load(patreonTiers);
				}
			} catch (IOException e) {
				Quark.LOG.error("Failed to load patreon information", e);
			}
		}

	}

}
