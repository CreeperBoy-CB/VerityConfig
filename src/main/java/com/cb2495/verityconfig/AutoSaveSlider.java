package com.cb2495.verityconfig;

import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.widget.ForgeSlider;

public class AutoSaveSlider extends ForgeSlider {
    private final Runnable onChanged;

    public AutoSaveSlider(int x, int y, int width, int height, Component prefix, Component suffix,
                          double min, double max, double value, double step, int precision, boolean drawString,
                          Runnable onChanged) {
        super(x, y, width, height, prefix, suffix, min, max, value, step, precision, drawString);
        this.onChanged = onChanged;
    }

    @Override
    protected void applyValue() {
        super.applyValue();
        if (onChanged != null) onChanged.run();
    }
}