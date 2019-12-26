package mcworldinspector;

import java.awt.Color;
import java.util.stream.Stream;

/**
 *
 * @author matthias
 */
public enum MCColor {
    WHITE("White", 0xF9FFFE),
    ORANGE("Orange", 0xF9801D),
    MAGENTA("Magenta", 0xC74EBD),
    LIGHT_BLUE("Light Blue", 0x3AB3DA),
    YELLOW("Yellow", 0xFED83D),
    LIME("Lime", 0x80C71F),
    PINK("Pink", 0xF38BAA),
    GRAY("Gray", 0x474F52),
    LIGHT_GRAY("Light Gray", 0x9D9D97),
    CYAN("Cyan", 0x169C9C),
    PURPLE("Purple", 0x8932B8),
    BLUE("Blue", 0x3C44AA),
    BROWN("Brown", 0x835432),
    GREEN("Green", 0x5E7C16),
    RED("Red", 0xB02E26),
    BLACK("Black", 0x1D1D21);
    
    private final String text;
    private final Color color;

    private MCColor(String text, int color) {
        this.text = text;
        this.color = new Color(color);
    }

    @Override
    public String toString() {
        return text;
    }

    public Color getColor() {
        return color;
    }
    
    public static MCColor fromByte(byte b) {
        MCColor[] values = values();
        return (b >= 0 && b < values.length) ? values[b] : null;
    }
    
    public static MCColor fromByte(Byte b) {
        if(b == null)
            return null;
        return fromByte(b.byteValue());
    }

    public static Stream<MCColor> asStream(byte b) {
        MCColor color = fromByte(b);
        return (color != null) ? Stream.of(color) : Stream.empty();
    }
    
    public static Stream<MCColor> asStream(Byte b) {
        MCColor color = fromByte(b);
        return (color != null) ? Stream.of(color) : Stream.empty();
    }
}
