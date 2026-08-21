package net.fire.emf.client.session;

import net.fire.emf.client.title.LootRarity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SessionModels {
	private SessionModels() {
	}

	public enum DisplayMode {
		ALL("Alle"),
		MOB("Mob Spezifisch");

		private final String label;

		DisplayMode(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}

		public DisplayMode next() {
			return this == ALL ? MOB : ALL;
		}

		public DisplayMode previous() {
			return next();
		}
	}

	public static final class LootStack {
		public String itemName = "";
		public String itemNameJson = "";
		public int count;
		public String hoverText = "";
		public List<String> hoverLineJsons = new ArrayList<>();
		public String rarity = LootRarity.COMMON.name();

		public LootStack() {
		}

		public LootStack(String itemName, String itemNameJson, int count, String hoverText, List<String> hoverLineJsons, LootRarity rarity) {
			this.itemName = itemName == null ? "" : itemName;
			this.itemNameJson = itemNameJson == null ? "" : itemNameJson;
			this.count = count;
			this.hoverText = hoverText == null ? "" : hoverText;
			this.hoverLineJsons = hoverLineJsons == null ? new ArrayList<>() : new ArrayList<>(hoverLineJsons);
			this.rarity = rarity == null ? LootRarity.COMMON.name() : rarity.name();
		}

		public LootRarity rarityEnum() {
			try {
				return LootRarity.valueOf(rarity);
			} catch (RuntimeException ignored) {
				return LootRarity.COMMON;
			}
		}
	}

	public static final class MobStats {
		public long kills;
		public List<LootStack> loot = new ArrayList<>();

		public void addKill(long amount) {
			kills += Math.max(0L, amount);
		}

		public void addLoot(String itemName, String itemNameJson, String hoverText, List<String> hoverLineJsons, LootRarity rarity) {
			addLoot(itemName, itemNameJson, hoverText, hoverLineJsons, rarity, 1);
		}

		public void addLoot(String itemName, String itemNameJson, String hoverText, List<String> hoverLineJsons, LootRarity rarity, int amount) {
			if (itemName == null || itemName.isBlank() || amount <= 0) {
				return;
			}
			for (LootStack stack : loot) {
				if (itemName.equalsIgnoreCase(stack.itemName)) {
					stack.count += amount;
					if (itemNameJson != null && !itemNameJson.isBlank()) {
						stack.itemNameJson = itemNameJson;
					}
					if (hoverText != null && !hoverText.isBlank()) {
						stack.hoverText = hoverText;
					}
					if (hoverLineJsons != null && !hoverLineJsons.isEmpty()) {
						stack.hoverLineJsons = new ArrayList<>(hoverLineJsons);
					}
					if (rarity != null && rarity.ordinal() > stack.rarityEnum().ordinal()) {
						stack.rarity = rarity.name();
					}
					return;
				}
			}
			loot.add(new LootStack(itemName, itemNameJson, amount, hoverText, hoverLineJsons, rarity));
		}

		public List<LootStack> sortedLoot() {
			List<LootStack> sorted = new ArrayList<>(loot);
			sorted.sort((a, b) -> Integer.compare(a.rarityEnum().ordinal(), b.rarityEnum().ordinal()));
			return sorted;
		}
	}

	public static final class LiveSession {
		public long startedAtMs;
		public long accumulatedActiveMs;
		public long lastTickMs;
		public boolean paused;
		public String currentMobName;
		public final Map<String, MobStats> mobs = new LinkedHashMap<>();

		public MobStats mob(String name) {
			return mobs.computeIfAbsent(name, ignored -> new MobStats());
		}

		public long elapsedMs(long now) {
			long total = accumulatedActiveMs;
			if (!paused && lastTickMs > 0L) {
				total += Math.max(0L, now - lastTickMs);
			}
			return Math.max(0L, total);
		}
	}

	public static final class SavedAllSession {
		public long id;
		public String savedAt = "";
		public long elapsedMs;
		public Map<String, MobStats> mobs = new LinkedHashMap<>();
	}

	public static final class SavedAllFile {
		public long nextId = 1L;
		public List<SavedAllSession> sessions = new ArrayList<>();
	}

	public static final class SavedMobSession {
		public long id;
		public String savedAt = "";
		public String mobName = "";
		public long elapsedMs;
		public long kills;
		public List<LootStack> loot = new ArrayList<>();
	}
}
