package mcworldinspector.utils;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;

/**
 *
 * @author matthias
 */
public class RangeSlider extends JComponent {

    private int min;
    private int max;
    private int lower;
    private int upper;

    private final Rectangle lowerRect = new Rectangle();
    private final Rectangle upperRect = new Rectangle();
    private final Rectangle trackRect = new Rectangle();

    private Icon thumbIcon;

    public RangeSlider() {
        this(0, 100);
    }

    @SuppressWarnings("LeakingThisInConstructor")
    public RangeSlider(int min, int max) {
        this.min = min;
        this.max = max;
        this.upper = max;

        LookAndFeel.installBorder(this, "Slider.border");
        LookAndFeel.installColorsAndFont(this, "Slider.background",
                "Slider.foreground", "Slider.font");

        thumbIcon = UIManager.getIcon("Slider.horizontalThumbIcon");
        if(thumbIcon == null)
            thumbIcon = new Icon() {
                @Override
                public void paintIcon(Component c, Graphics g, int x, int y) {
                    g.setColor(MetalLookAndFeel.getControlDarkShadow());
                    g.fillRect(x+2, y+2, 8, 18);
                    g.setColor(MetalLookAndFeel.getControlHighlight());
                    g.fillRect(x  , y  , 8, 18);
                    g.setColor(getForeground());
                    g.fillRect(x+1, y+1, 8, 18);
                    g.setColor(MetalLookAndFeel.getControlHighlight());
                    g.drawLine(x+5, y+4, x+5, y+14);
                    g.setColor(MetalLookAndFeel.getControlDarkShadow());
                    g.drawLine(x+6, y+4, x+6, y+14);
                }

                @Override
                public int getIconWidth() {
                    return 10;
                }

                @Override
                public int getIconHeight() {
                    return 20;
                }
            };
        lowerRect.setSize(thumbIcon.getIconWidth(), thumbIcon.getIconHeight());
        upperRect.setSize(thumbIcon.getIconWidth(), thumbIcon.getIconHeight());

        final MouseAdapter ma = new MouseAdapter() {
            private IntConsumer setValue;

            @Override
            public void mouseDragged(MouseEvent e) {
                if(setValue != null && trackRect.width > 0) {
                    int value = clamp((e.getX() - trackRect.x) *
                            (max - min) / trackRect.width + min, min, max);
                    setValue.accept(value);
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                boolean inLower = lowerRect.contains(e.getX(), e.getY());
                boolean inUpper = upperRect.contains(e.getX(), e.getY());
                int middle = (lowerRect.x + upperRect.x) / 2;
                if(inLower && (!inUpper || e.getX() < middle))
                    setValue = RangeSlider.this::setLower;
                else if(inUpper)
                    setValue = RangeSlider.this::setUpper;
                else
                    setValue = null;
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    public int getMin() {
        return min;
    }

    public void setMin(int min) {
        if(this.min != min) {
            this.min = min;
            repaint();
        }
    }

    public int getMax() {
        return max;
    }

    public void setMax(int max) {
        if(this.max != max) {
            this.max = max;
            repaint();
        }
    }

    public int getLower() {
        return lower;
    }

    public void setLower(int lower) {
        lower = clamp(lower, min, max - 1);
        if(this.lower != lower) {
            int oldLower = this.lower;
            int oldUpper = this.upper;
            this.lower = lower;
            if(lower >= upper) {
                this.upper = lower + 1;
                firePropertyChange("upper", oldUpper, upper);
            }
            firePropertyChange("lower", oldLower, lower);
            repaint();
        }
    }

    public int getUpper() {
        return upper;
    }

    public void setUpper(int upper) {
        upper = clamp(upper, min + 1, max);
        if(this.upper != upper) {
            int oldLower = this.lower;
            int oldUpper = this.upper;
            this.upper = upper;
            if(upper <= lower) {
                this.lower = upper - 1;
                firePropertyChange("lower", oldLower, lower);
            }
            firePropertyChange("upper", oldUpper, upper);
            repaint();
        }
    }

    @Override
    public Dimension getPreferredSize() {
        Insets insets = calculateTrackRect();
        return new Dimension(200, insets.top + insets.bottom + trackRect.height);
    }

    @Override
    public Dimension getMinimumSize() {
        Insets insets = calculateTrackRect();
        return new Dimension(40, insets.top + insets.bottom + trackRect.height);
    }

    private int scalePosition(int value) {
        if(min != max)
            return (value - min) * trackRect.width / (max - min);
        else
            return 0;
    }

    private void updateLowerPosition() {
        lowerRect.x = trackRect.x + scalePosition(lower) - lowerRect.width / 2;
        lowerRect.y = trackRect.y;
    }

    private void updateUpperPosition() {
        upperRect.x = trackRect.x + scalePosition(upper) - upperRect.width / 2;
        upperRect.y = trackRect.y;
    }

    @Override
    protected void paintComponent(Graphics g) {
        if(isOpaque()) {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
        }

        calculateTrackRect();
        updateLowerPosition();
        updateUpperPosition();

        paintTrack(g);
        thumbIcon.paintIcon(this, g, lowerRect.x, lowerRect.y);
        thumbIcon.paintIcon(this, g, upperRect.x, upperRect.y);
    }

    private void paintTrack(Graphics g) {
        int trackThickness = (int)(lowerRect.height * 7 / 16);
        int thumbOverhang = (lowerRect.height - trackThickness) / 2;
        int trackBottom = trackRect.y + (trackRect.height - 1) - thumbOverhang;
        int trackTop = trackRect.y + trackBottom - trackThickness + 1;
        int trackLeft = trackRect.x;
        int trackRight = trackRect.x + trackRect.width - 1;

        g.setColor(MetalLookAndFeel.getControlDarkShadow());
        g.drawRect(trackLeft, trackTop,
                    (trackRight - trackLeft) - 1, (trackBottom - trackTop) - 1);

        g.setColor(MetalLookAndFeel.getControlHighlight());
        g.drawLine(trackLeft + 1, trackBottom, trackRight, trackBottom);
        g.drawLine(trackRight, trackTop + 1, trackRight, trackBottom);

        g.setColor(MetalLookAndFeel.getControlShadow());
        g.drawLine(trackLeft + 1, trackTop + 1, trackRight - 2, trackTop + 1);
        g.drawLine(trackLeft + 1, trackTop + 1, trackLeft + 1, trackBottom - 2);

        int fillTop = isEnabled() ? trackTop : trackTop + 1;
        int fillBottom = isEnabled() ? trackBottom - 1 : trackBottom - 2;
        int fillLeft = lowerRect.x + lowerRect.width / 2;
        int fillRight = upperRect.x + upperRect.width / 2;

        g.setColor(getBackground());
        g.drawLine(fillLeft, fillTop, fillRight, fillTop);
        g.drawLine(fillLeft, fillTop, fillLeft, fillBottom);

        g.setColor(MetalLookAndFeel.getControlShadow());
        g.fillRect(fillLeft + 1, fillTop + 1,
                    fillRight - fillLeft, fillBottom - fillTop);
    }

    private Insets calculateTrackRect() {
        Insets insets = getInsets();
        int width = getWidth() - insets.left - insets.right;
        int height = getHeight() - insets.top - insets.bottom;
        trackRect.x = insets.left + lowerRect.width / 2;
        trackRect.y = insets.top + (height - lowerRect.height - 1) / 2;
        trackRect.width = width - lowerRect.width;
        trackRect.height = lowerRect.height;
        return insets;
    }
}
