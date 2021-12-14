package org.zkoss.exporter.util;

import org.zkoss.zk.ui.Component;
import org.zkoss.zul.Label;

/**
 * Extract the text in the following order:
 * 1. first {@link Label} child
 * 2. parent component's label, text, or value attribute
 * 3. empty string
 */
public class LabelExtractor implements TextExtractor{

    @Override
    public String getText(Component parentComponent) {
        if (parentComponent.getFirstChild() instanceof Label){
            return ((Label)parentComponent.getFirstChild()).getValue();
        }
        Object textObject = Utils.invokeComponentGetter(parentComponent, "getLabel", "getText", "getValue");
        if (textObject!= null){
            return textObject.toString();
        }
        return "";
    }
}
