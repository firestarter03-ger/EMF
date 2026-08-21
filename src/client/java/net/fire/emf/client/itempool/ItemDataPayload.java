package net.fire.emf.client.itempool;

import java.util.ArrayList;
import java.util.List;

/** Anzeige-/Sync-Daten eines Items (1:1 unverändert speichern und hochladen). */
public final class ItemDataPayload {
	public String itemName = "";
	public String itemNameJson = "";
	public String hoverText = "";
	public List<String> hoverLineJsons = new ArrayList<>();
}
