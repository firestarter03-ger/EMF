package net.fire.emf.client.itempool;

import net.fire.emf.client.session.SessionComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Liest Item-Komponenten aus und erzeugt eine stabile SHA-256-{@code itemId}.
 * Hasht nicht den kompletten Command-String, sondern Registry-ID + kanonische JSONs.
 */
public final class ItemIdentifier {
	private static final Pattern COLOR_CODES = Pattern.compile("§x(?:§[0-9a-fA-F]){6}|§[0-9a-fk-orA-FK-OR]");

	private ItemIdentifier() {
	}

	public record IdentifiedItem(
			String itemId,
			String registryId,
			String itemName,
			ItemDataPayload itemData) {
	}

	public static IdentifiedItem identify(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		String registryId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		Component nameComponent = stack.get(DataComponents.CUSTOM_NAME);
		if (nameComponent == null) {
			nameComponent = stack.getHoverName();
		}
		String itemName = clean(nameComponent.getString());
		String itemNameJson = SessionComponents.toJson(nameComponent);

		List<String> hoverLineJsons = new ArrayList<>();
		StringBuilder hoverPlain = new StringBuilder();
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null) {
			for (Component line : lore.lines()) {
				if (line == null) {
					hoverLineJsons.add(SessionComponents.toJson(Component.literal(" ")));
					appendHoverLine(hoverPlain, "");
					continue;
				}
				hoverLineJsons.add(SessionComponents.toJson(line));
				appendHoverLine(hoverPlain, clean(line.getString()));
			}
		}

		ItemDataPayload data = new ItemDataPayload();
		data.itemName = itemName;
		data.itemNameJson = itemNameJson;
		data.hoverText = hoverPlain.toString();
		data.hoverLineJsons = hoverLineJsons;

		String itemId = computeItemId(registryId, itemNameJson, hoverLineJsons);
		return new IdentifiedItem(itemId, registryId, itemName, data);
	}

	public static String computeItemId(String registryId, String itemNameJson, List<String> hoverLineJsons) {
		String canonicalName = JsonCanonicalizer.canonicalize(itemNameJson);
		List<String> canonicalLines = JsonCanonicalizer.canonicalizeList(hoverLineJsons);
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			update(digest, registryId == null ? "" : registryId);
			digest.update((byte) 0);
			update(digest, canonicalName);
			digest.update((byte) 0);
			for (String line : canonicalLines) {
				update(digest, line);
				digest.update((byte) 0);
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 nicht verfügbar", exception);
		}
	}

	private static void update(MessageDigest digest, String value) {
		digest.update(value.getBytes(StandardCharsets.UTF_8));
	}

	private static void appendHoverLine(StringBuilder text, String line) {
		if (!text.isEmpty()) {
			text.append('\n');
		}
		text.append(line == null ? "" : line);
	}

	private static String clean(String value) {
		return value == null ? "" : COLOR_CODES.matcher(value).replaceAll("").trim();
	}
}
