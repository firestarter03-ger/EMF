package net.fire.emf.client.itempool;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Erkennt erhaltene Items (Inventar-Scan + Loot-Chat ShowItem) und reicht sie an {@link ItemPoolManager} weiter.
 */
public final class ItemReceiveTracker {
	private static final int SCAN_INTERVAL_TICKS = 20;

	private static int tickCounter;
	private static boolean registered;

	private ItemReceiveTracker() {
	}

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		ClientTickEvents.END_CLIENT_TICK.register(ItemReceiveTracker::onTick);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> tickCounter = 0);
	}

	/**
	 * Loot-/Chat-Nachricht: ShowItem aus Hover extrahieren und anbieten.
	 */
	public static void onChatMessage(Component message) {
		if (message == null || !SpecialItemManager.get().isReady()) {
			return;
		}
		ItemStack stack = extractShowItem(message);
		if (stack != null && !stack.isEmpty()) {
			ItemPoolManager.get().offer(stack);
		}
	}

	private static void onTick(Minecraft client) {
		if (client == null || client.player == null || client.level == null) {
			return;
		}
		if (!SpecialItemManager.get().isReady()) {
			return;
		}
		tickCounter++;
		if (tickCounter < SCAN_INTERVAL_TICKS) {
			return;
		}
		tickCounter = 0;
		scanInventory(client.player.getInventory());
		offer(client.player.getMainHandItem());
		offer(client.player.getOffhandItem());
	}

	private static void scanInventory(Inventory inventory) {
		if (inventory == null) {
			return;
		}
		int size = inventory.getContainerSize();
		for (int i = 0; i < size; i++) {
			offer(inventory.getItem(i));
		}
	}

	private static void offer(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		ItemPoolManager.get().offer(stack);
	}

	private static ItemStack extractShowItem(Component message) {
		AtomicReference<ItemStack> found = new AtomicReference<>();
		message.visit((style, ignored) -> {
			if (found.get() != null || style == null) {
				return Optional.empty();
			}
			HoverEvent hover = style.getHoverEvent();
			if (hover instanceof HoverEvent.ShowItem showItem) {
				try {
					ItemStack stack = showItem.item().create();
					if (stack != null && !stack.isEmpty()) {
						found.set(stack);
					}
				} catch (RuntimeException ignoredError) {
				}
			}
			return Optional.empty();
		}, Style.EMPTY);
		return found.get();
	}
}
