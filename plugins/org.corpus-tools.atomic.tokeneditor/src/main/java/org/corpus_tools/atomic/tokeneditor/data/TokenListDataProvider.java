/**
 * 
 */
package org.corpus_tools.atomic.tokeneditor.data;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.corpus_tools.salt.common.SDocumentGraph;
import org.corpus_tools.salt.common.SSpan;
import org.corpus_tools.salt.common.SToken;
import org.corpus_tools.salt.core.SAnnotation;
import org.eclipse.nebula.widgets.nattable.data.IColumnAccessor;
import org.eclipse.nebula.widgets.nattable.data.ISpanningDataProvider;
import org.eclipse.nebula.widgets.nattable.data.ListDataProvider;
import org.eclipse.nebula.widgets.nattable.layer.cell.DataCell;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

/**
 * TODO Description
 *
 * @author Stephan Druskat <mail@sdruskat.net>
 *
 */
public class TokenListDataProvider extends ListDataProvider<SToken> implements ISpanningDataProvider {

	private final SDocumentGraph graph;
	
	private final Multimap<String, String> uniqueSpanAnnotations = ArrayListMultimap.create();
	private final Multimap<String, String> uniqueTokenAnnotations = ArrayListMultimap.create();
	private final Multimap<String, String> uniqueAnnotations = ArrayListMultimap.create();
	private int colCount;
	private int tokenTextColIndex;

	/**
	 * @param list
	 * @param columnAccessor
	 */
	public TokenListDataProvider(SDocumentGraph graph, IColumnAccessor<SToken> columnAccessor) {
		super(graph.getSortedTokenByText(), columnAccessor);
		this.graph = graph;
//		initialize();
	}
	
	/**
	 * TODO: Description
	 *
	 * @param graph2
	 */
	private void initialize() {
		/*
		 * Find out how many columns we need!
		 * NOTE: Rows will be tokens, columns will be annotations, etc. 
		 */
		for (SSpan span : graph.getSpans()) {
			for (SAnnotation annotation : span.getAnnotations()) {
				uniqueSpanAnnotations.put(annotation.getNamespace(), annotation.getName());
			}
		}
		for (SToken token : graph.getTokens()) {
			for (SAnnotation annotation : token.getAnnotations()) {
				uniqueTokenAnnotations.put(annotation.getNamespace(), annotation.getName());
			}
		}
		uniqueAnnotations.putAll(uniqueSpanAnnotations);
		uniqueAnnotations.putAll(uniqueTokenAnnotations);
		colCount = 2; // Token text and token indices
		for (Entry<String, Collection<String>> entry : uniqueAnnotations.asMap().entrySet()) {
			Set<String> uniqueNames  = new HashSet<>(entry.getValue());
			for (String name : uniqueNames) {
				colCount++;
			}
		}
		tokenTextColIndex = uniqueSpanAnnotations.asMap().values().size() + 1;
		System.err.println(tokenTextColIndex + " span annos");
	}
	
    @Override
    public int getColumnCount() {
//        return this.columnAccessor.getColumnCount();
    	return this.list.size();
    }

    @Override
    public int getRowCount() {
//        return this.list.size();
    	return this.columnAccessor.getColumnCount();
    }

    @Override
    public Object getDataValue(int columnIndex, int rowIndex) {
        SToken colObj = this.list.get(columnIndex);
        return this.columnAccessor.getDataValue(colObj, rowIndex);
    }

    @Override
    public void setDataValue(int columnIndex, int rowIndex, Object newValue) {
    	// FIXME Check if implemenatation is necessary
//        SToken colObj = this.list.get(columnIndex);
//        this.columnAccessor.setDataValue(colObj, rowIndex, newValue);
    }

    @Override
    public SToken getRowObject(int rowIndex) {
        return this.list.get(rowIndex);
    }

    @Override
    public int indexOfRowObject(SToken rowObject) {
        return this.list.indexOf(rowObject);
    }

    @Override
	public List<SToken> getList() {
        return this.list;
    }

	/* (non-Javadoc)
	 * @see org.eclipse.nebula.widgets.nattable.data.ISpanningDataProvider#getCellByPosition(int, int)
	 */
	@Override
	public DataCell getCellByPosition(int columnPosition, int rowPosition) {
		// TODO Auto-generated method stub
		return null;
	}


}
