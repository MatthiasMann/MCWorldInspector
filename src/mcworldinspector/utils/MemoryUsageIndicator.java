package mcworldinspector.utils;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 *
 * @author matthias
 */
public class MemoryUsageIndicator extends JComponent implements ActionListener {

    private static final double MB = 1 << 20;

    private final int minHistorySize;
    private final Timer timer;
    private long[] history;
    private int head;
    private String text = "";
    private long totalMemory = 1;
    private Color barColor = new Color(0x8a9103);

    @SuppressWarnings("OverridableMethodCallInConstructor")
    public MemoryUsageIndicator(int historySize) {
        this.minHistorySize = historySize;
        this.timer = new Timer(1000, this);
        setToolTipText("Current memory usage / heap size - click to force garbage collection");
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Runtime.getRuntime().gc();
            }
        });
    }

    public Color getBarColor() {
        return barColor;
    }

    public void setBarColor(Color barColor) {
        this.barColor = barColor;
    }

    public void start() {
        timer.start();
    }

    public void stop() {
        timer.stop();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Runtime runtime = Runtime.getRuntime();
        totalMemory = runtime.totalMemory();
        long usedMemory = totalMemory - runtime.freeMemory();
        text = String.format("%.1f/%.1fMB", usedMemory / MB, totalMemory / MB);
        if(history != null)
            history[head++ % history.length] = usedMemory;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        Font font = getFont();
        FontMetrics fontMetrics = getFontMetrics(font);
        return new Dimension(Math.max(minHistorySize*4,
                fontMetrics.charWidth('0')*14), fontMetrics.getHeight());
    }

    @Override
    protected void paintComponent(Graphics g) {
        final int width = getWidth();
        final int height = getHeight();
        final Color background = getBackground();
        g.setColor(background);
        g.fillRect(0, 0, width, height);

        if(history != null) {
            final int histLen = history.length;

            long maxUsed = totalMemory;
            for(long used : history)
                maxUsed = Math.max(maxUsed, used);

            for(int idx=histLen,x=width ; idx-- > 0 ;) {
                long used = history[(head + idx) % histLen];
                int scaled = (int)((height * used + maxUsed - 1) / maxUsed);
                float t = (idx+1) / (float)histLen;
                t *= 0.8f;
                t += 0.2f;
                g.setColor(new Color(
                        (int)lerp(background.getRed(), barColor.getRed(), t),
                        (int)lerp(background.getGreen(), barColor.getGreen(), t),
                        (int)lerp(background.getBlue(), barColor.getBlue(), t)));
                g.fillRect(x -= 4, height - scaled, 4, scaled);
            }

            if(histLen != width/4 + 1)
                history = null;
        }

        if(history == null)
            history = new long[width/4 + 1];

        g.setColor(getForeground());
        final FontMetrics fontMetrics = g.getFontMetrics();
        int textWidth = fontMetrics.stringWidth(text);
        g.drawString(text, (width - textWidth)/2, fontMetrics.getAscent());
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
