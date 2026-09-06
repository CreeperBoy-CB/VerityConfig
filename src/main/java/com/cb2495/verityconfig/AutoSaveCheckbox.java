package com.cb2495.verityconfig;

import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.network.chat.Component;

public class AutoSaveCheckbox extends Checkbox {
    private final Runnable onChanged;

    public AutoSaveCheckbox(int x, int y, int width, int height, Component message, boolean selected, Runnable onChanged) {
        super(x, y, width, height, message, selected);
        this.onChanged = onChanged;
    }

    @Override
    public void onPress() {
        super.onPress();
        if (onChanged != null) onChanged.run();
    }
}