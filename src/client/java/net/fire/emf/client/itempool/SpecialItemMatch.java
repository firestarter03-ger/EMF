package net.fire.emf.client.itempool;

import java.util.ArrayList;
import java.util.List;

/**
 * Match-Regeln für Spezial-Items. Erste Version: exakter {@code itemName}.
 * Später erweiterbar um registryId, hoverContains*, Regex usw.
 */
public final class SpecialItemMatch {
	public String registryId;
	public String itemName;
	public List<String> hoverContains;
	public List<String> hoverContainsAny;
	public List<String> hoverContainsAll;
	public String itemNameRegex;
}
