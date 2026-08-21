package net.fire.emf.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fire.emf.client.api.EmfApiClient;
import net.fire.emf.client.command.EmfCommands;
import net.fire.emf.client.itempool.ItemPoolManager;
import net.fire.emf.client.itempool.ItemReceiveTracker;
import net.fire.emf.client.config.EmfConfig;
import net.fire.emf.client.config.EmfDataFolder;
import net.fire.emf.client.debug.DialogScreenDebug;
import net.fire.emf.client.function.OffhandSwapper;
import net.fire.emf.client.overlay.LevelInfoOverlay;
import net.fire.emf.client.overlay.AutominerCooldownOverlay;
import net.fire.emf.client.overlay.SkillFruitOverlay;
import net.fire.emf.client.overlay.SkillInfoOverlay;
import net.fire.emf.client.overlay.editor.OverlayEditorUtility;
import net.fire.emf.client.resource.CollectionScanner;
import net.fire.emf.client.resource.ProfileDetector;
import net.fire.emf.client.session.SessionTracker;
import net.fire.emf.client.title.TitleAlerts;

import java.nio.file.Files;

public class ElementsMoreFeaturesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EmfDataFolder.initialize();
		EmfConfig.HANDLER.load();
		if (!Files.exists(EmfDataFolder.configFile())) {
			EmfConfig.HANDLER.save();
		}
		ItemPoolManager.get().initialize();
		ItemReceiveTracker.register();
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			OffhandSwapper.syncHotkeyToConfig();
			EmfConfig.HANDLER.save();
		});
		SkillInfoOverlay.register();
		LevelInfoOverlay.register();
		SkillFruitOverlay.register();
		AutominerCooldownOverlay.register();
		TitleAlerts.register();
		SessionTracker.register();
		EmfApiClient.get().initialize();
		EmfCommands.register();
		DialogScreenDebug.register();
		CollectionScanner.register();
		ProfileDetector.register();
		OffhandSwapper.initialize();
		OverlayEditorUtility.initialize();
	}
}
