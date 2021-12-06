package org.zkoss.exporter.util;

import org.zkoss.zk.ui.Component;

/**
 * To extract the text from a parent component. Because a container-like component like <code>&lt;foot/></code> can have nested, complicated structure to enclose multiple labels. So there is no way for an exporter to know the component structure and extract data. Hence, the exporter user who knows the layout of his Grid/Listbox should implement his own way to extract labels and return a String to exporter.
 * @param <C>
 */
public interface TextExtractor<C extends Component> {
    /**
     * implement your own way to get {@link org.zkoss.zul.Label} or to compose a text here. Because only Exporter users know your a foot's layout.
     * @param parentComponent could be a footer
     * @return
     */
    public String getText(C parentComponent);
}
