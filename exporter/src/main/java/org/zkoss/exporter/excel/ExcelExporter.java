/* ExcelExporter.java

{{IS_NOTE
	Purpose:
		
	Description:
		
	History:
		Nov 12, 2012 10:58:26 AM , Created by Sam
}}IS_NOTE

Copyright (C) 2012 Potix Corporation. All Rights Reserved.

{{IS_RIGHT
}}IS_RIGHT
*/
package org.zkoss.exporter.excel;

import static org.zkoss.exporter.util.Utils.getAlign;
import static org.zkoss.exporter.util.Utils.getFoot;
import static org.zkoss.exporter.util.Utils.getHeaderSize;
import static org.zkoss.exporter.util.Utils.getHeaders;
import static org.zkoss.exporter.util.Utils.getStringValue;
import static org.zkoss.exporter.util.Utils.getTarget;

import java.io.IOException;
import java.io.OutputStream;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.zkoss.exporter.AbstractExporter;
import org.zkoss.exporter.GroupRenderer;
import org.zkoss.exporter.RowRenderer;
import org.zkoss.exporter.excel.imp.CellValueSetterFactoryImpl;
import org.zkoss.exporter.util.*;
import org.zkoss.poi.ss.usermodel.*;
import org.zkoss.poi.ss.util.CellRangeAddress;
import org.zkoss.poi.xssf.usermodel.*;
import org.zkoss.zk.ui.Component;
import org.zkoss.zul.Auxhead;
import org.zkoss.zul.Listcell;
import org.zkoss.zul.Span;
import org.zkoss.zul.impl.HeaderElement;
import org.zkoss.zul.impl.MeshElement;

/**
 * @author Sam
 *
 */
public class ExcelExporter extends AbstractExporter <XSSFWorkbook, Row> {

	private ExportContext _exportContext;
	private CellValueSetterFactory _cellValueSetterFactory;

	public <D> void export(int columnSize, Collection<D> data, RowRenderer<Row, D> renderer, OutputStream outputStream) throws IOException {
		XSSFWorkbook book = new XSSFWorkbook();
		ExportContext ctx = new ExportContext(true, book.createSheet("Sheet1"));
		XSSFSheet sheet = ctx.getSheet();
		setExportContext(ctx);
		
		if (getInterceptor() != null)
			getInterceptor().beforeRendering(book);
		int rowIndex = 0;
		for (D d : data) {
			renderer.render(getOrCreateRow(ctx.moveToNextRow(), sheet), d, (rowIndex++ % 2 == 1));
		}
		if (getInterceptor() != null)
			getInterceptor().afterRendering(book);
		
		adjustColumnWidth(columnSize);
		
		book.write(outputStream);
		setExportContext(null);
	}
	
	public <D> void export(int columnSize, Collection< Collection <D> > data, GroupRenderer<Row, D> renderer, OutputStream outputStream) throws IOException {
		XSSFWorkbook book = new XSSFWorkbook();
		ExportContext ctx = new ExportContext(true, book.createSheet("Sheet1"));
		XSSFSheet sheet = ctx.getSheet();
		setExportContext(ctx);
		
		if (getInterceptor() != null)
			getInterceptor().beforeRendering(book);
		
		int rowIndex = 0;
		for (Collection <D> group : data) {
			renderer.renderGroup(getOrCreateRow(ctx.moveToNextRow(), sheet), group);
			for (D d : group) {
				renderer.render(getOrCreateRow(ctx.moveToNextRow(), sheet), d, (rowIndex++ % 2 == 1));
			}
			renderer.renderGroupfoot(getOrCreateRow(ctx.moveToNextRow(), sheet), group);
		}
		
		if (getInterceptor() != null)
			getInterceptor().afterRendering(book);
		
		adjustColumnWidth(columnSize);
		book.write(outputStream);
		setExportContext(null);
	}
	
	public void setExportContext(ExportContext ctx) {
		_exportContext = ctx;
	}
	
	public ExportContext getExportContext() {
		return _exportContext;
	}
	
	public void setCellValueSetterFactory(CellValueSetterFactory cellValueSetterFactory) {
		_cellValueSetterFactory = cellValueSetterFactory;
	}
	
	public CellValueSetterFactory getCellValueSetterFactory() {
		if (_cellValueSetterFactory == null) {
			_cellValueSetterFactory = new CellValueSetterFactoryImpl();
		}
		return _cellValueSetterFactory;
	}
	
	private void adjustColumnWidth(int columnSize) {
		XSSFSheet sheet = getExportContext().getSheet();
		for (int c = 0; c < columnSize; c++) {
			sheet.autoSizeColumn(c);
		}
	}
	
	@Override
	protected void exportTabularComponent(MeshElement meshElement, OutputStream outputStream) throws Exception {
		XSSFWorkbook book = new XSSFWorkbook();
		setExportContext(new ExportContext(true, book.createSheet("Sheet1")));
		
		int columnSize = getHeaderSize(meshElement);
		exportHeaders(columnSize, meshElement, book);
		exportRows(columnSize, meshElement, book);
		exportFooters(columnSize, meshElement, book);
		
		adjustColumnWidth(columnSize);
		
		book.write(outputStream);
		setExportContext(null);
	}

	@Override
	protected void exportAuxhead(int columnSize, Auxhead auxhead, XSSFWorkbook book) {
		//TODO: process row span
		exportCellsWithSpan(columnSize, auxhead, book, new LabelExtractor());
	}
	
	private void setCellAlignment(short alignment, Cell cell, XSSFWorkbook book) {
		if (cell.getCellStyle().getAlignment() != alignment) {
			XSSFCellStyle cellStyle = book.createCellStyle();
			cellStyle.cloneStyleFrom(cell.getCellStyle());
			cellStyle.setAlignment(alignment);
			cell.setCellStyle(cellStyle);
		}
	}

	private boolean syncAlignment(Component cmp, Cell cell, XSSFWorkbook book) {
		if (cmp == null)
			return false;
		
		final String align = getAlign(cmp);
		if ("center".equals(align)) {
			setCellAlignment(CellStyle.ALIGN_CENTER, cell, book);
			return true;
		} else if ("right".equals(align)) {
			setCellAlignment(CellStyle.ALIGN_RIGHT, cell, book);
			return true;
		} else if ("left".equals(align)) {
			setCellAlignment(CellStyle.ALIGN_LEFT, cell, book);
			return true;
		}
		return false;
	}
	
	
	private void syncAlignment(Component cmp, Component header, Cell cell, XSSFWorkbook book) {
		//check if component define align, if not check header's align
		if (!syncAlignment(cmp, cell, book) && header != null) {
			syncAlignment(header, cell, book);
		}
	}

	/**
	 * column header are bold.
	 * @param columns
	 * @param book
	 */
	@Override
	protected void exportColumnHeaders(Component columns, XSSFWorkbook book) {
		CellValueSetter<Component> cellValueSetter = getCellValueSetterFactory().getCellValueSetter(Component.class);
		ExportContext ctx = getExportContext();
		XSSFSheet sheet = ctx.getSheet();
		for (Component column : columns.getChildren()) {
			Cell cell = getOrCreateCell(ctx.moveToNextCell(), sheet);
			cellValueSetter.setCellValue(column, cell);
			applyBoldColoredStyle(book, cell);
			syncAlignment(column, cell, book);
		}
		ctx.moveToNextRow();
	}

	/* bold, background color */
	private void applyBoldColoredStyle(XSSFWorkbook book, Cell cell) {
		XSSFCellStyle cellStyle = book.createCellStyle();
		cellStyle.cloneStyleFrom(cell.getCellStyle());
		XSSFFont font = book.createFont();
		font.setBold(true);
		cellStyle.setFont(font);
		cellStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
		cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		cell.setCellStyle(cellStyle);
	}

	@Override
	protected void exportGroup(int columnSize, Component group, XSSFWorkbook book) {
		Iterator<Component> iterator = group.getChildren().iterator();
		
		CellValueSetter<Component> cellValueSetter = getCellValueSetterFactory().getCellValueSetter(Component.class);
		ExportContext context = getExportContext();
		XSSFSheet sheet = context.getSheet();
		while (iterator.hasNext()) {
			Component cmp = iterator.next();
			
			Cell cell = getOrCreateCell(context.moveToNextCell(), sheet);
			cellValueSetter.setCellValue(cmp, cell);
			applyBoldColoredStyle(book, cell);
		}
		context.moveToNextRow();
	}

	@Override
	protected void exportGroupfoot(int columnSize, Component groupfoot,	XSSFWorkbook book) {
		exportCellsWithSpan(columnSize, groupfoot, book, new LabelExtractor());
	}

	@Override
	protected void exportCells(int rowIndex, int columnSize, Component row,	XSSFWorkbook book) {
		CellValueSetter<Component> cellValueSetter = getCellValueSetterFactory().getCellValueSetter(Component.class);
		ExportContext ctx = getExportContext();
		XSSFSheet sheet = ctx.getSheet();
		
		HashMap<Integer, Component> headers = buildHeaderIndexMap(getHeaders(getTarget(row)));
		List<Component> children = row.getChildren();
		for (int c =0; c < columnSize; c++) {
			Component cmp = c < children.size() ? children.get(c) : null;
			
			if (cmp == null) {
				ctx.moveToNextRow();
				return;
			}
			
			Cell cell = getOrCreateCell(ctx.moveToNextCell(), sheet);
		    if(isExportAsString(cmp)){
		         RichTextString xssfRichTextString = new XSSFRichTextString(Utils.getStringValue(cmp)); 
		         cell.setCellValue(xssfRichTextString); 
		     } else {
		    	boolean cellProcessed = false;
		    	String value = cmp.isVisible() ? (String) getStringValue(cmp) : "";
		    	value = value != null ? value.trim() : value;
		    	
				// Try to format number only when the label does not contain alphabets
				if(StringUtils.isNotEmpty(value) && !value.matches(".*[a-z A-Z/].*")) {
					XSSFDataFormat dataFormat = book.createDataFormat();
					XSSFCellStyle currencyStyle = book.createCellStyle();
					Number numberValue;
					try {
						if(value.endsWith("%")) {
							numberValue = NumberFormat.getPercentInstance().parse(value);
							cell.setCellValue(value.contains(".") ? numberValue.floatValue() : numberValue.doubleValue());
							
							currencyStyle.setDataFormat(dataFormat.getFormat(value.contains(".") ? "0.00%" : "0%"));
						} else {
							numberValue = NumberFormat.getCurrencyInstance().parse(value);
							cell.setCellValue(value.contains(".") ? numberValue.floatValue() : numberValue.doubleValue());
							
							currencyStyle.setDataFormat(dataFormat.getFormat(value.contains(".") ? "\"$\"#,##0.00_);[Red](\"$\"#,##0.00)" : "\"$\"#,##0_);[Red](\"$\"#,##0)"));
						}
						cell.setCellStyle(currencyStyle);
						cellProcessed = true;
					} catch (ParseException e) {
						// Do not throw exception and let the cell value be set as-is in the end
						try {
							numberValue = NumberFormat.getNumberInstance().parse(value);
							cell.setCellValue(value.contains(".") ? numberValue.floatValue() : numberValue.doubleValue());
							currencyStyle.setDataFormat(dataFormat.getFormat(value.contains(".") ? "#,##0.00_);[Red](#,##0.00)" : "#,##0_);[Red](#,##0)"));
							cell.setCellStyle(currencyStyle);
							cellProcessed = true;
						} catch (ParseException pe) {
							// Do not throw exception and let the cell value be set as-is in the end
						}
					}
				} else if(cmp instanceof Listcell) {
					Listcell listcell = (Listcell) cmp;
					if(listcell.getIconSclass() != null && listcell.getIconSclass().contains("fa-")) {
						cell.setCellValue("Y");
						cellProcessed = true;
					}
				} else if(cmp.getFirstChild() != null && cmp.getFirstChild() instanceof Span) {  
					Span span = (Span) cmp.getFirstChild();
					if(span != null && span.getSclass() != null && span.getSclass().contains("fa-")) {
						cell.setCellValue("Y");
						cellProcessed = true;
					}
				} else if(cmp instanceof Span) {  
					Span span = (Span) cmp;
					if(span != null && span.getSclass() != null && span.getSclass().contains("fa-")) {
						cell.setCellValue("Y");
						cellProcessed = true;
					}
				} else if (cmp instanceof org.zkoss.zul.Cell){
					exportCell(cell, (org.zkoss.zul.Cell) cmp, rowIndex, c, ctx);
					cellProcessed = true;
				} else if(StringUtils.isEmpty(value)) {
					cell.setCellValue("");
					cellProcessed = true;
				}
				
				if(!cellProcessed) {
					cellValueSetter.setCellValue(cmp, cell);
				}
		     }
			
			syncAlignment(cmp, headers != null ? headers.get(c) : null, cell, book);
		}
		ctx.moveToNextRow();
	}

	private boolean isExportAsString(Component cmp) {
		return cmp.getAttribute("exportAsString") != null && (cmp.getAttribute("exportAsString").equals("true") || cmp.getAttribute("exportAsString").equals(true));
	}

	private void exportCell(Cell cell, org.zkoss.zul.Cell zkCell, int rowIndex, int colIndex, ExportContext ctx) {
		int colspan = zkCell.getColspan();
		cell.setCellValue(getStringValue(zkCell.getFirstChild()));
		if (colspan > 1) {
			ctx.getSheet().addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, colIndex, colIndex + colspan - 1));
		}
	}

	@Override
	protected void exportFooters(int columnSize, Component meshElement, XSSFWorkbook book) {
		Component foot = getFoot(meshElement);
		if (foot == null) {
			return;
		}

		exportCellsWithSpan(columnSize, foot, book, this.footTextExtractor);
	}

	/*
	 * assume <cell colspan></cell> in a <groupfoot/> instead of the deprecated attribute <groupfoot spans>.
	 */
	private void exportCellsWithSpan(int columnSize, Component component, XSSFWorkbook book, TextExtractor textExtractor) {
		ExportContext ctx = getExportContext();
		XSSFSheet sheet = ctx.getSheet();
		for (Component cmp : component.getChildren()) {
			int colIndex = 0;
			int colSpan = getColSpan(cmp);
			Cell cell = getOrCreateCell(ctx.moveToNextCell(), sheet);
			cell.setCellValue(textExtractor.getText(cmp));
			if (colSpan > 1) {
				ctx.getSheet().addMergedRegion(new CellRangeAddress(ctx.getRowIndex(), ctx.getRowIndex(), colIndex, colIndex + colSpan - 1));
			}
			applyBoldColoredStyle(book, cell);
			colIndex++;
		}
		ctx.moveToNextRow();
	}
	
	private HashMap<Integer, Component> buildHeaderIndexMap(Component target) {
		if (target == null)
			return null;
		
		HashMap<Integer, Component> headers = new HashMap<Integer, Component>();
		
		int idx = 0;
		for (Component c : target.getChildren()) {
			if (!(c instanceof HeaderElement)) {
				throw new IllegalArgumentException(c + " is not type of HeaderElement");
			}
			headers.put(idx++, c);
		}
		
		return headers;
	}
	
	public static Row getOrCreateRow(int[] idx, Sheet sheet) {
		return getOrCreateRow(idx[0], sheet);
	}
	
	public static Row getOrCreateRow(int row, Sheet sheet) {
		Row r = sheet.getRow(row);
		if (r == null) {
			return sheet.createRow(row);
		}
		return r;
	}

	public static Cell getOrCreateCell(int[] idx, Sheet sheet) {
		return getOrCreateCell(idx[0], idx[1], sheet);
	}
	
	public static Cell getOrCreateCell(int row, int col, Sheet sheet) {
		Row r = getOrCreateRow(row, sheet);
		Cell cell = r.getCell(col);
		if (cell == null) {
			return r.createCell(col);
		}
		return cell;
	}
	
	public static class ExportContext {
		int _rowIndex = -1;
		int _columnIndex = -1;
		final XSSFSheet _sheet;
		final boolean _exportByComponentReference;
		
		ExportContext (boolean isExportByComponentReference, XSSFSheet worksheet) {
			_exportByComponentReference = isExportByComponentReference;
			_sheet = worksheet;
		}
		
		public boolean isExportByComponentReference() {
			return _exportByComponentReference;
		}
		
		public void setRowIndex(int rowIndex) {
			_rowIndex = rowIndex;
		}
		
		public int getRowIndex() {
			return _rowIndex;
		}
		
		public void setColumnIndex(int columnIndex) {
			_columnIndex = columnIndex;
		}
		
		public int getColumnIndex() {
			return _columnIndex;
		}
		
		public int[] moveToNextCell() {
			return new int[]{_rowIndex < 0 ? _rowIndex = 0 : _rowIndex, _columnIndex < 0 ? _columnIndex = 0 : ++_columnIndex};
		}
		
		public int[] moveToNextRow() {
			return new int[]{++_rowIndex, _columnIndex = -1};
		}
		
		public XSSFSheet getSheet() {
			return _sheet;
		}
	}

	//TODO: not tested yet
	@Override
	public <D> void export(String[] columnHeaders, Collection<D> data,
			RowRenderer<Row, D> renderer, OutputStream outputStream) throws Exception {
		final int columnSize = columnHeaders.length;
		
		//TODO: need to log if not ExportColumnHeaderInterceptorImpl ?
		if (getInterceptor() == null)
			setInterceptor(new ExportColumnHeaderInterceptorImpl(columnHeaders));
		export(columnSize, data, renderer, outputStream);
	}
	
	//TODO: not tested yet
	@Override
	public <D> void export(String[] columnHeaders, Collection<Collection<D>> data,
			GroupRenderer<Row, D> renderer,	OutputStream outputStream) throws Exception {
		
		if (getInterceptor() == null)
			setInterceptor(new ExportColumnHeaderInterceptorImpl(columnHeaders));
		
		export(columnHeaders, data, renderer, outputStream);
	}
	
	//export header
	private class ExportColumnHeaderInterceptorImpl implements org.zkoss.exporter.Interceptor <XSSFWorkbook> {

		private final String[] _columnHeaders;
		public ExportColumnHeaderInterceptorImpl(String[] columnHeaders) {
			_columnHeaders = columnHeaders;
		}
		
		@Override
		public void beforeRendering(XSSFWorkbook book) {
			int columnSize = _columnHeaders.length;
			boolean renderHeader = false;
			for (int i = 0; i < columnSize; i++) {
				String e = _columnHeaders[i];
				if (e != null && e.length() > 0) {
					renderHeader = true;
					break;
				}
			}
			if (renderHeader) {
				ExportContext ctx = getExportContext();
				XSSFSheet sheet = ctx.getSheet();
				
				for (String header : _columnHeaders) {
					getOrCreateCell(ctx.moveToNextCell(), sheet).setCellValue(header);
				}
			}
		}

		@Override
		public void afterRendering(XSSFWorkbook book) {
		}
	}
}