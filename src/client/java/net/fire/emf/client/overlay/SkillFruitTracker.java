package net.fire.emf.client.overlay;

import net.fire.emf.client.config.EmfConfig;
import net.fire.emf.client.config.EmfConfig.SkillFruitTimer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SkillFruitTracker {
	private static final Pattern COLOR_CODES = Pattern.compile("§x(?:§[0-9a-fA-F]){6}|§[0-9a-fk-orA-FK-OR]");
	private static final Pattern FRUIT = Pattern.compile(
			"(?i)(Mining|Farming|Foraging|Fishing|Crafting|Combat|Magic)\\s*-?\\s*frucht");
	private static final long COOLDOWN_MS = 30L * 60L * 1000L;

	private static boolean useHeld;

	private SkillFruitTracker() {
	}

	public static void onUse(Player player, InteractionHand hand) {
		if (player == null || hand != InteractionHand.MAIN_HAND || useHeld) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client != null && client.screen != null) {
			return;
		}
		String fruit = fruitName(player.getItemInHand(hand));
		if (fruit == null) {
			return;
		}
		useHeld = true;
		start(fruit);
	}

	public static void tick(Minecraft client) {
		boolean rightDown = client != null && client.getWindow() != null
				&& GLFW.glfwGetMouseButton(client.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_2) == GLFW.GLFW_PRESS;
		if (!rightDown) {
			useHeld = false;
			return;
		}
		if (useHeld || client.player == null || client.screen != null) {
			return;
		}
		onUse(client.player, InteractionHand.MAIN_HAND);
	}

	public static boolean shouldRender() {
		if (!EmfConfig.skillFruitOverlayVisible()) {
			return false;
		}
		return !visibleTimers().isEmpty();
	}

	public static OverlaySnapshot snapshot() {
		List<FruitLine> lines = new ArrayList<>();
		long now = System.currentTimeMillis();
		for (SkillFruitTimer timer : visibleTimers()) {
			lines.add(new FruitLine(title(timer), formatDuration(remainingMs(timer, now))));
		}
		return lines.isEmpty() ? null : new OverlaySnapshot(List.copyOf(lines));
	}

	public static OverlaySnapshot previewSnapshot() {
		return new OverlaySnapshot(List.of(
				new FruitLine("Foragingfrucht (2x)", "45min"),
				new FruitLine("Combatfrucht (1x)", "15min")));
	}

	private static void start(String fruit) {
		EmfConfig config = EmfConfig.HANDLER.instance();
		migrateLegacy(config);
		ensureTimerList(config);
		long now = System.currentTimeMillis();
		pruneExpired(config, now);
		SkillFruitTimer existing = findTimer(config.skillFruitTimers, fruit);
		if (existing != null && remainingMs(existing, now) > 0L) {
			existing.stacks = Math.max(1, existing.stacks) + 1;
			existing.untilMs += COOLDOWN_MS;
		} else if (existing != null) {
			existing.stacks = 1;
			existing.untilMs = now + COOLDOWN_MS;
		} else {
			SkillFruitTimer timer = new SkillFruitTimer();
			timer.name = fruit;
			timer.stacks = 1;
			timer.untilMs = now + COOLDOWN_MS;
			config.skillFruitTimers.add(timer);
		}
		EmfConfig.HANDLER.save();
	}

	private static List<SkillFruitTimer> visibleTimers() {
		EmfConfig config = EmfConfig.HANDLER.instance();
		migrateLegacy(config);
		ensureTimerList(config);
		long now = System.currentTimeMillis();
		boolean alwaysShow = config.skillFruitAlwaysShow;
		List<SkillFruitTimer> visible = new ArrayList<>();
		for (SkillFruitTimer timer : config.skillFruitTimers) {
			if (timer == null || timer.name == null || timer.name.isBlank()) {
				continue;
			}
			if (alwaysShow || remainingMs(timer, now) > 0L) {
				visible.add(timer);
			}
		}
		return visible;
	}

	private static void migrateLegacy(EmfConfig config) {
		ensureTimerList(config);
		if (!config.skillFruitTimers.isEmpty()) {
			return;
		}
		if (config.skillFruitName == null || config.skillFruitName.isBlank() || config.skillFruitCooldownUntilMs <= 0L) {
			return;
		}
		SkillFruitTimer timer = new SkillFruitTimer();
		timer.name = config.skillFruitName;
		timer.stacks = 1;
		timer.untilMs = config.skillFruitCooldownUntilMs;
		config.skillFruitTimers.add(timer);
		config.skillFruitName = "";
		config.skillFruitCooldownUntilMs = 0L;
		EmfConfig.HANDLER.save();
	}

	private static void ensureTimerList(EmfConfig config) {
		if (config.skillFruitTimers == null) {
			config.skillFruitTimers = new ArrayList<>();
		}
	}

	private static void pruneExpired(EmfConfig config, long now) {
		if (config.skillFruitAlwaysShow) {
			return;
		}
		config.skillFruitTimers.removeIf(timer -> timer == null || remainingMs(timer, now) <= 0L);
	}

	private static SkillFruitTimer findTimer(List<SkillFruitTimer> timers, String fruit) {
		for (SkillFruitTimer timer : timers) {
			if (timer != null && timer.name != null && timer.name.equalsIgnoreCase(fruit)) {
				return timer;
			}
		}
		return null;
	}

	private static String title(SkillFruitTimer timer) {
		int stacks = Math.max(1, timer.stacks);
		return timer.name + " (" + stacks + "x)";
	}

	private static long remainingMs(SkillFruitTimer timer, long now) {
		if (timer.untilMs <= 0L) {
			return 0L;
		}
		return Math.max(0L, timer.untilMs - now);
	}

	private static String fruitName(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		String plain = COLOR_CODES.matcher(stack.getHoverName().getString()).replaceAll("").trim();
		if (plain.isBlank()) {
			return null;
		}
		Matcher matcher = FRUIT.matcher(plain);
		return matcher.find() ? matcher.group().replaceAll("\\s+", "") : null;
	}

	static String formatDuration(long remainingMs) {
		if (remainingMs <= 0L) {
			return "0sek";
		}
		if (remainingMs < 60_000L) {
			return Math.max(1L, (remainingMs + 999L) / 1000L) + "sek";
		}
		return (remainingMs / 60_000L) + "min";
	}

	public record FruitLine(String title, String duration) {
	}

	public record OverlaySnapshot(List<FruitLine> lines) {
	}
}
