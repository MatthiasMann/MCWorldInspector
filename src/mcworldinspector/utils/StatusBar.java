package mcworldinspector.utils;

import java.awt.Component;
import java.util.ArrayList;
import java.util.function.Consumer;
import javax.swing.GroupLayout;
import javax.swing.JPanel;
import javax.swing.LayoutStyle;

/**
 *
 * @author matthias
 */
public class StatusBar extends JPanel {

    private final ArrayList<Element> elements = new ArrayList<>();
    private final GroupLayout layout = new GroupLayout(this);
    private int firstRightAligned = 0;

    @SuppressWarnings("OverridableMethodCallInConstructor")
    public StatusBar() {
        super(null);
        setLayout(layout);
    }
    
    public void addElement(Element element) {
        elements.add(firstRightAligned, element);
        if(element.alignment == Alignment.LEFT)
            firstRightAligned++;

        final GroupLayout.ParallelGroup vertical = layout.createParallelGroup();
        final GroupLayout.SequentialGroup horizontal = layout.createSequentialGroup().addContainerGap();
        elements.forEach(new Consumer<Element>() {
            Alignment prev = Alignment.LEFT;
            boolean first = true;
            @Override
            public void accept(Element e) {
                if(e.alignment == Alignment.RIGHT && prev == Alignment.LEFT)
                    horizontal.addGap(0, 0, Short.MAX_VALUE);
                else {
                    assert(e.alignment == prev);
                    if(!first)
                        horizontal.addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED);
                }
                horizontal.addComponent(e.widget);
                vertical.addComponent(e.widget);
                prev = e.alignment;
                first = false;
            }
        });
        layout.setHorizontalGroup(horizontal.addContainerGap());
        layout.setVerticalGroup(vertical);
    }

    public static enum Alignment {
        LEFT, RIGHT;
    }

    public static class Element {
        final Alignment alignment;
        final Component widget;

        public Element(Alignment alignment, Component widget) {
            this.alignment = alignment;
            this.widget = widget;
        }

    }
    
}
