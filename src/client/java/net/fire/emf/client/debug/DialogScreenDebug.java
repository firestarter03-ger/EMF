package net.fire.emf.client.debug;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fire.emf.ElementsMoreFeatures;
import net.fire.emf.client.config.EmfConfig;
import net.fire.emf.client.mixin.DialogScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.ButtonListDialog;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.ConfirmationDialog;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.SimpleDialog;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.ItemBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class DialogScreenDebug {
	private static final Logger LOGGER = ElementsMoreFeatures.LOGGER;
	private static String lastDump = "";

	private DialogScreenDebug() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(DialogScreenDebug::tick);
	}

	private static void tick(Minecraft client) {
		if (!EmfConfig.debugDialogScreen()) {
			lastDump = "";
			return;
		}

		String dump = describe(client.screen, client);
		if (dump.equals(lastDump)) {
			return;
		}
		lastDump = dump;
		LOGGER.info("[Dialog Debug] {}", dump);
	}

	private static String describe(Screen screen, Minecraft client) {
		if (screen == null) {
			return "DialogScreen erkannt: nein (kein Screen offen)";
		}
		if (!(screen instanceof DialogScreen<?> dialogScreen)) {
			return "DialogScreen erkannt: nein (screen=" + screen.getClass().getName()
					+ ", titel='" + text(screen.getTitle()) + "')";
		}

		StringBuilder out = new StringBuilder();
		out.append("DialogScreen erkannt: ja");
		out.append(" | screen=").append(dialogScreen.getClass().getSimpleName());

		Dialog dialog;
		try {
			dialog = ((DialogScreenAccessor) dialogScreen).emf$getDialog();
		} catch (Throwable error) {
			out.append(" | Dialog-Objekt: FEHLER ").append(error.getClass().getSimpleName())
					.append(": ").append(error.getMessage());
			return out.toString();
		}

		if (dialog == null) {
			out.append(" | Dialog-Objekt: null");
			return out.toString();
		}

		out.append(" | Dialog-Typ=").append(dialog.getClass().getSimpleName());
		CommonDialogData common = dialog.common();
		String title = text(common.title());
		out.append(" | Screen.getTitle='").append(text(screen.getTitle())).append("'");
		out.append(" | Dialog.title='").append(title).append("'");
		common.externalTitle().ifPresent(external ->
				out.append(" | externalTitle='").append(text(external)).append("'"));

		List<DialogBody> body = common.body();
		out.append(" | Body (").append(body.size()).append("): ");
		if (body.isEmpty()) {
			out.append("leer");
		} else {
			for (int i = 0; i < body.size(); i++) {
				if (i > 0) {
					out.append(" ; ");
				}
				out.append("[").append(i).append("] ").append(describeBody(body.get(i), client));
			}
		}

		List<Input> inputs = common.inputs();
		out.append(" | Inputs (").append(inputs.size()).append("): ");
		if (inputs.isEmpty()) {
			out.append("keine");
		} else {
			for (int i = 0; i < inputs.size(); i++) {
				if (i > 0) {
					out.append(" ; ");
				}
				Input input = inputs.get(i);
				out.append("[").append(i).append("] key=").append(input.key())
						.append(" control=").append(input.control().getClass().getSimpleName())
						.append(" ").append(String.valueOf(input.control()));
			}
		}

		List<ActionButton> buttons = collectButtons(dialog);
		out.append(" | Buttons/Actions (").append(buttons.size()).append("): ");
		if (buttons.isEmpty()) {
			out.append("keine");
		} else {
			for (int i = 0; i < buttons.size(); i++) {
				if (i > 0) {
					out.append(" ; ");
				}
				out.append("[").append(i).append("] ").append(describeButton(buttons.get(i)));
			}
		}

		dialog.onCancel().ifPresent(cancel ->
				out.append(" | CancelAction=").append(cancel.getClass().getSimpleName()));

		boolean elementsMenu = looksLikeElementsMenu(title);
		boolean resourcebagButton = buttons.stream().anyMatch(button -> looksLikeResourcebag(text(button.button().label())));
		out.append(" | Prüfung Elements-Menü: Titel-Match=").append(elementsMenu)
				.append(", Button Resourcebag gefunden=").append(resourcebagButton);

		int itemCount = 0;
		int textCount = 0;
		int hoverCount = 0;
		boolean resourcebagHeading = looksLikeResourcebag(title);
		for (DialogBody entry : body) {
			if (entry instanceof ItemBody itemBody) {
				itemCount++;
				if (itemBody.description().isPresent()) {
					textCount++;
					Component description = itemBody.description().get().contents();
					if (!collectHovers(description).isEmpty()) {
						hoverCount++;
					}
				}
			} else if (entry instanceof PlainMessage message) {
				if (looksLikeResourcebag(text(message.contents()))) {
					resourcebagHeading = true;
				}
			}
		}
		boolean resourcebagReadable = itemCount > 0 && textCount > 0;
		out.append(" | Prüfung Resourcebag: Überschrift-Match=").append(resourcebagHeading)
				.append(", Items=").append(itemCount)
				.append(", Text=").append(textCount)
				.append(", HoverText=").append(hoverCount)
				.append(", Struktur lesbar=").append(resourcebagReadable);

		return out.toString();
	}

	private static List<ActionButton> collectButtons(Dialog dialog) {
		List<ActionButton> buttons = new ArrayList<>();
		if (dialog instanceof SimpleDialog simple) {
			buttons.addAll(simple.mainActions());
		}
		if (dialog instanceof MultiActionDialog multi) {
			addUnique(buttons, multi.actions());
			multi.exitAction().ifPresent(button -> addUnique(buttons, List.of(button)));
		}
		if (dialog instanceof ConfirmationDialog confirmation) {
			addUnique(buttons, List.of(confirmation.yesButton(), confirmation.noButton()));
		}
		if (dialog instanceof NoticeDialog notice) {
			addUnique(buttons, List.of(notice.action()));
		}
		if (dialog instanceof ButtonListDialog buttonList) {
			buttonList.exitAction().ifPresent(button -> addUnique(buttons, List.of(button)));
		}
		return buttons;
	}

	private static void addUnique(List<ActionButton> target, List<ActionButton> extra) {
		for (ActionButton button : extra) {
			if (button != null && !target.contains(button)) {
				target.add(button);
			}
		}
	}

	private static String describeBody(DialogBody body, Minecraft client) {
		if (body instanceof ItemBody itemBody) {
			StringBuilder out = new StringBuilder("ItemBody");
			out.append(" item=").append(describeItem(itemBody.item(), client));
			out.append(" showItemTooltip=").append(itemBody.showTooltip());
			out.append(" showDecorations=").append(itemBody.showDecorations());
			itemBody.description().ifPresent(description ->
					out.append(" ").append(describeTextComponent("text", description.contents())));
			return out.toString();
		}
		if (body instanceof PlainMessage message) {
			return "PlainMessage " + describeTextComponent("text", message.contents());
		}
		return body.getClass().getSimpleName() + " " + body;
	}

	private static String describeTextComponent(String label, Component component) {
		StringBuilder out = new StringBuilder(label).append("='").append(text(component)).append("'");
		List<String> hovers = collectHovers(component);
		out.append(" hover=").append(hovers.isEmpty() ? "keine" : hovers);
		List<String> clicks = collectClicks(component);
		if (!clicks.isEmpty()) {
			out.append(" click=").append(clicks);
		}
		return out.toString();
	}

	private static List<String> collectHovers(Component component) {
		List<String> found = new ArrayList<>();
		if (component == null) {
			return found;
		}
		component.visit((style, ignored) -> {
			HoverEvent hover = style.getHoverEvent();
			if (hover != null) {
				String described = describeHover(hover);
				if (!found.contains(described)) {
					found.add(described);
				}
			}
			return Optional.empty();
		}, Style.EMPTY);
		return found;
	}

	private static List<String> collectClicks(Component component) {
		List<String> found = new ArrayList<>();
		if (component == null) {
			return found;
		}
		component.visit((style, ignored) -> {
			ClickEvent click = style.getClickEvent();
			if (click != null) {
				String described = click.getClass().getSimpleName() + " " + click;
				if (!found.contains(described)) {
					found.add(described);
				}
			}
			return Optional.empty();
		}, Style.EMPTY);
		return found;
	}

	private static String describeHover(HoverEvent hover) {
		if (hover instanceof HoverEvent.ShowText showText) {
			return "ShowText='" + text(showText.value()) + "'";
		}
		if (hover instanceof HoverEvent.ShowItem showItem) {
			return "ShowItem=" + showItem;
		}
		if (hover instanceof HoverEvent.ShowEntity showEntity) {
			return "ShowEntity=" + showEntity;
		}
		return hover.getClass().getSimpleName() + " " + hover;
	}

	private static String describeItem(ItemStackTemplate template, Minecraft client) {
		if (template == null) {
			return "leer";
		}

		ItemStack stack;
		try {
			stack = template.create();
		} catch (Throwable error) {
			return "template=" + template + " create-Fehler=" + error.getClass().getSimpleName();
		}
		if (stack == null || stack.isEmpty()) {
			return "leer";
		}

		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		StringBuilder out = new StringBuilder(id);
		out.append(" name='").append(text(stack.getHoverName())).append("'");
		out.append(" count=").append(stack.getCount());

		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null && !lore.lines().isEmpty()) {
			out.append(" lore=").append(joinComponents(lore.lines()));
		}

		List<Component> tooltip = readTooltip(stack, client);
		if (!tooltip.isEmpty()) {
			out.append(" itemTooltip=").append(joinComponents(tooltip));
		}
		return out.toString();
	}

	private static List<Component> readTooltip(ItemStack stack, Minecraft client) {
		try {
			if (client.level == null) {
				return List.of();
			}
			Item.TooltipContext context = Item.TooltipContext.of(client.level);
			TooltipFlag flag = client.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL;
			return stack.getTooltipLines(context, client.player, flag);
		} catch (Throwable ignored) {
			return List.of();
		}
	}

	private static String describeButton(ActionButton button) {
		CommonButtonData data = button.button();
		StringBuilder out = new StringBuilder("'").append(text(data.label())).append("'");
		data.tooltip().ifPresent(tooltip -> out.append(" tooltip='").append(text(tooltip)).append("'"));
		out.append(" action=").append(button.action()
				.map(action -> action.getClass().getSimpleName() + " " + action)
				.orElse("keine"));
		return out.toString();
	}

	private static boolean looksLikeElementsMenu(String title) {
		String normalized = normalize(title);
		boolean hasElements = normalized.contains("element") || normalized.contains("elemts");
		boolean hasMenu = normalized.contains("menü") || normalized.contains("menu") || normalized.contains("menue");
		return hasElements && hasMenu;
	}

	private static boolean looksLikeResourcebag(String value) {
		String normalized = normalize(value);
		return normalized.contains("resourcebag")
				|| normalized.contains("resourcenbag")
				|| normalized.contains("resorucenbag")
				|| normalized.contains("resourceenbag");
	}

	private static String joinComponents(List<Component> components) {
		List<String> lines = new ArrayList<>();
		for (Component component : components) {
			String line = text(component);
			if (!line.isBlank()) {
				lines.add("'" + line + "'");
			}
		}
		return lines.toString();
	}

	private static String text(Component component) {
		return component == null ? "" : component.getString();
	}

	private static String normalize(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
	}
}
