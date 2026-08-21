package net.fire.emf.client.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fire.emf.client.api.EmfApiClient;
import net.fire.emf.client.config.EmfConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class EmfCommands {
	private EmfCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> registerCommands(dispatcher));
	}

	private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(literal("emf")
				.then(literal("server")
						.requires(source -> EmfConfig.debugApi())
						.then(literal("status")
								.executes(context -> showServerStatus(context.getSource())))
						.then(literal("refresh")
								.executes(context -> refreshServer(context.getSource())))));
	}

	private static int showServerStatus(FabricClientCommandSource source) {
		if (!EmfConfig.debugApi()) {
			source.sendError(Component.literal("Nur verfügbar, wenn „Debug Server“ in der Config aktiv ist.")
					.withStyle(ChatFormatting.RED));
			return 0;
		}
		EmfApiClient api = EmfApiClient.get();
		boolean tracking = api.isTrackingEnabled();
		boolean registered = api.isRegistered();
		boolean hasToken = api.playerToken() != null && !api.playerToken().isBlank();

		source.sendFeedback(Component.literal("===DEBUG SERVER STATUS===").withStyle(ChatFormatting.GOLD));
		source.sendFeedback(line("Verbindung", tracking ? "Aktiviert" : "Deaktiviert", tracking));
		source.sendFeedback(line("Registriert", registered ? "Ja" : "Nein", registered));
		source.sendFeedback(line("Token erhalten", hasToken ? "Ja" : "Nein", hasToken));
		source.sendFeedback(Component.literal("=====================").withStyle(ChatFormatting.GOLD));
		return 1;
	}

	private static int refreshServer(FabricClientCommandSource source) {
		if (!EmfConfig.debugApi()) {
			source.sendError(Component.literal("Nur verfügbar, wenn „Debug Server“ in der Config aktiv ist.")
					.withStyle(ChatFormatting.RED));
			return 0;
		}
		if (!EmfConfig.isDataTrackingEnabled()) {
			source.sendError(Component.literal("Datentracking ist deaktiviert — Registrierung nicht möglich.")
					.withStyle(ChatFormatting.RED));
			return 0;
		}

		source.sendFeedback(Component.literal("Token wird erneuert…").withStyle(ChatFormatting.YELLOW));
		CompletableFuture.runAsync(() -> {
			EmfApiClient.RefreshResult result = EmfApiClient.get().refreshRegistrationSync();
			Minecraft client = Minecraft.getInstance();
			client.execute(() -> {
				switch (result) {
					case SUCCESS -> source.sendFeedback(Component.literal("Registrierung aktualisiert — neues Token erhalten.")
							.withStyle(ChatFormatting.GREEN));
					case FALLBACK -> source.sendFeedback(Component.literal(
									"Re-Register fehlgeschlagen — altes Token wiederhergestellt.")
							.withStyle(ChatFormatting.YELLOW));
					case FAILED -> source.sendError(Component.literal(
									"Re-Register fehlgeschlagen — kein Token verfügbar.")
							.withStyle(ChatFormatting.RED));
				}
			});
		});
		return 1;
	}

	private static Component line(String label, String value, boolean ok) {
		return Component.literal(label + ": ")
				.withStyle(ChatFormatting.GRAY)
				.append(Component.literal(value).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
	}
}
