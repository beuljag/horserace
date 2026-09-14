package net.teujaem.horserace.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** '&' 색코드 문자열 <-> Component 변환 도우미. */
public final class Text {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private Text() {
    }

    /** 아이템/GUI 용. 기본 이탤릭을 꺼서 바닐라 표시와 어긋나지 않게 한다. */
    public static Component of(String legacy) {
        return LEGACY.deserialize(legacy).decoration(TextDecoration.ITALIC, false);
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static String strip(String legacy) {
        return plain(LEGACY.deserialize(legacy));
    }
}
