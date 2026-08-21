package net.fire.emf.client.itempool;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validiert Special-Items-Dokumente vor Persistenz und Anwendung.
 */
public final class SpecialItemsValidator {
	private SpecialItemsValidator() {
	}

	public static boolean isValid(SpecialItemsDocument document) {
		if (document == null) {
			return false;
		}
		if (document.revision < 1) {
			return false;
		}
		if (document.specialItems == null) {
			return false;
		}
		Set<String> specialIds = new HashSet<>();
		Set<String> itemIds = new HashSet<>();
		for (SpecialItemDefinition definition : document.specialItems) {
			if (!isValidDefinition(definition, specialIds, itemIds)) {
				return false;
			}
		}
		return true;
	}

	private static boolean isValidDefinition(
			SpecialItemDefinition definition,
			Set<String> specialIds,
			Set<String> itemIds) {
		if (definition == null) {
			return false;
		}
		if (definition.specialId == null || definition.specialId.isBlank()) {
			return false;
		}
		if (!specialIds.add(definition.specialId.trim())) {
			return false;
		}
		if (definition.match == null || !hasAnyMatchRule(definition.match)) {
			return false;
		}
		if (definition.item == null) {
			return false;
		}
		if (definition.item.itemId == null || definition.item.itemId.isBlank()) {
			return false;
		}
		if (!itemIds.add(definition.item.itemId.trim())) {
			return false;
		}
		if (definition.item.itemName == null || definition.item.itemName.isBlank()) {
			return false;
		}
		if (definition.item.itemData == null) {
			return false;
		}
		if (definition.item.itemData.hoverLineJsons == null) {
			definition.item.itemData.hoverLineJsons = List.of();
		}
		return true;
	}

	private static boolean hasAnyMatchRule(SpecialItemMatch match) {
		return (match.itemName != null && !match.itemName.isBlank())
				|| (match.registryId != null && !match.registryId.isBlank())
				|| notEmpty(match.hoverContains)
				|| notEmpty(match.hoverContainsAny)
				|| notEmpty(match.hoverContainsAll)
				|| (match.itemNameRegex != null && !match.itemNameRegex.isBlank());
	}

	private static boolean notEmpty(List<String> list) {
		return list != null && !list.isEmpty();
	}
}
