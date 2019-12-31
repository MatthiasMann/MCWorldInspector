package mcworldinspector.utils;

import java.awt.EventQueue;
import java.util.Collections;
import java.util.List;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;

/**
 *
 * @author matthias
 */
public class TextFieldAutoCompletion implements DocumentListener {

    private final JTextComponent jtc;
    private final List<String> words;

    public TextFieldAutoCompletion(JTextComponent jtc, List<String> words) {
        this.jtc = jtc;
        this.words = words;
    }

    public static TextFieldAutoCompletion install(JTextComponent jtc, List<String> words) {
        TextFieldAutoCompletion tfac = new TextFieldAutoCompletion(jtc, words);
        jtc.getDocument().addDocumentListener(tfac);
        return tfac;
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        if(e.getLength() != 1)
            return;
        try {
            final int offset = e.getOffset() + 1;
            final String text = e.getDocument().getText(0, offset);
            final int wordStart = text.lastIndexOf(' ') + 1;
            final String word = text.substring(wordStart);
            final int pos = Collections.binarySearch(words, word);
            if(pos < 0) {
                final String completion = words.get(~pos);
                if(completion.startsWith(word)) {
                    final String tail = completion.substring(offset - wordStart);
                    EventQueue.invokeLater(() -> {
                        if(jtc.getCaretPosition() == offset) {
                            jtc.replaceSelection(tail);
                            jtc.setSelectionStart(offset);
                            jtc.setSelectionEnd(offset + tail.length());
                        }
                    });
                }
            }
        } catch (BadLocationException ex) {}
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
    }
}
