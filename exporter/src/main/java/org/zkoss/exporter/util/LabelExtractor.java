package org.zkoss.exporter.util;

import org.zkoss.zk.ui.Component;
import org.zkoss.zul.Label;

/**
 * Assume the parent component has only 1 {@link Label}.
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
