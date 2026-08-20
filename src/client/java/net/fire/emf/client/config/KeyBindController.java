package net.fire.emf.client.config;

import com.mojang.blaze3d.platform.InputConstants;
import dev.isxander.yacl3.api.Controller;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.utils.Dimension;
import dev.isxander.yacl3.gui.AbstractWidget;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.controllers.ControllerWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class KeyBindController implements Controller<String> {
	private final Option<String> option;

	public KeyBindController(Option<String> option) {
		this.option = option;
	}

	@Override
	public Option<String> option() {
		return option;
	}

	@Override
	public Component formatValue() {
		return keyDisplay(option.pendingValue());
	}

	@Override
	public AbstractWidget provideWidget(YACLScreen screen, Dimension<Integer> widgetDimension) {
		return new KeyBindControllerElement(this, screen, widgetDimension);
	}

	static Component keyDisplay(String keyName) {
		try {
			return InputConstants.getKey(keyName).getDisplayName();
		} catch (RuntimeException ignored) {
			return Component.literal(keyName == null || keyName.isBlank() ? "Nicht belegt" : keyName);
		}
	}

	public static final class KeyBindControllerElement extends ControllerWidget<KeyBindController> {
		private boolean listening;

		public KeyBindControllerElement(KeyBindController control, YACLScreen screen, Dimension<Integer> dim) {
			super(control, screen, dim);
		}

		@Override
		protected int getHoveredControlWidth() {
			return Minecraft.getInstance().font.width(getValueText());
		}

		@Override
		protected Component getValueText() {
			if (listening) {
				return Component.literal("> ... <").withStyle(ChatFormatting.YELLOW);
			}
			return control.formatValue();
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
			if (!isAvailable() || !isMouseOver(event.x(), event.y()) || event.button() != 0) {
				return false;
			}
			playDownSound();
			listening = !listening;
			setFocused(listening);
			return true;
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			if (!listening) {
				return false;
			}
			if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
				listening = false;
				setFocused(false);
				return true;
			}
			control.option().requestSet(InputConstants.getKey(event).getName());
			listening = false;
			setFocused(false);
			return true;
		}

		@Override
		public void unfocus() {
			listening = false;
			super.unfocus();
		}
	}
}
