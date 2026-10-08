/*
 * Client-Side Vein Miner - Copyright (c) 2026 C8L8M
 * SPDX-License-Identifier: LGPL-3.0-only
 * See LICENSE and licenses/GPL-3.0.txt in the project root.
 */
package com.c8l8m.clientveinminer.config;

import com.google.gson.JsonElement;

import fi.dy.masa.malilib.config.options.ConfigColor;
import fi.dy.masa.malilib.util.data.Color4f;

/**
 * A colour option without an alpha channel.
 *
 * malilib's stock {@link ConfigColor} round-trips a full ARGB value, so every one of this mod's
 * colours carried an alpha component that the mod never honoured (the preview outline is always
 * drawn opaque and the target fills take their alpha from the highlight opacity option). This
 * subclass removes the channel for good: values are forced opaque on the way in and always written
 * back as "#RRGGBB", so neither the config file nor the config screen ever shows alpha again.
 */
public class ConfigRgbColor extends ConfigColor
{
    public ConfigRgbColor(String name, String defaultValue, String comment)
    {
        super(name, toRgbHex(defaultValue), comment);
    }

    @Override
    public String getStringValue()
    {
        return toRgbHex(super.getStringValue());
    }

    @Override
    public String getDefaultStringValue()
    {
        return toRgbHex(super.getDefaultStringValue());
    }

    @Override
    public void setValueFromString(String value)
    {
        super.setValueFromString(toRgbHex(value));
    }

    @Override
    public void setIntegerValue(int value)
    {
        super.setIntegerValue(value | 0xFF000000);
    }

    @Override
    public void setValueFromJsonElement(JsonElement element)
    {
        if (element.isJsonPrimitive())
        {
            // Also normalises values written by older versions, which still stored an alpha component.
            super.setValueFromString(toRgbHex(element.getAsString()));
        }
        else
        {
            super.setValueFromJsonElement(element);
        }
    }

    /** Parses the value the way malilib does, then throws the alpha component away. */
    private static String toRgbHex(String value)
    {
        return String.format("#%06X", Color4f.fromString(value).getIntValue() & 0x00FFFFFF);
    }
}
