/**
 * 
 */
package org.corpus_tools.atomic.tokeneditor.accessors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import org.corpus_tools.salt.common.SDocumentGraph;
import org.corpus_tools.salt.common.SSpan;
import org.corpus_tools.salt.common.STextualRelation;
import org.corpus_tools.salt.common.SToken;
import org.corpus_tools.salt.core.SAnnotation;
import org.corpus_tools.salt.core.SNode;
import org.corpus_tools.salt.core.SRelation;
import org.eclipse.nebula.widgets.nattable.data.IColumnPropertyAccessor;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

/**
 * TODO Description
 *
 * @author Stephan Druskat <mail@sdruskat.net>
 *
 */
public class TokenRowPropertyAccessor implements IColumnPropertyAccessor<SToken> {

	private final SDocumentGraph graph;
	
	private final Multimap<String, String> uniqueSpanAnnotations = ArrayListMultimap.create();
	private final Multimap<String, String> uniqueTokenAnnotations = ArrayListMultimap.create();
	private final Multimap<String, String> uniqueAnnotations = ArrayListMultimap.create();
	private int colCount;
	private int tokenTextColIndex;

	private ArrayList<Object> spanAnnoNamesList;

	private ArrayList<Object> tokenAnnoNamesList;

	// private final List<String> propertyNames = Arrays.asList("text",
	// "offsets");

	/**
	 * @param graph
	 */
	public TokenRowPropertyAccessor(SDocumentGraph graph) {
		this.graph = graph;
		initialize();
	}
	
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
		spanAnnoNamesList = new ArrayList<>();
		for (Entry<String, Collection<String>> entry : uniqueSpanAnnotations.asMap().entrySet()) {
			Set<String> uniqueNames  = new HashSet<>(entry.getValue());
			for (String name : uniqueNames) {
				spanAnnoNamesList.add(name);
			}
		}
//		ArrayList<Collection<String>> spanAnnotationNameList = new ArrayList<>();
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
		tokenAnnoNamesList = new ArrayList<>();
		for (Entry<String, Collection<String>> entry : uniqueTokenAnnotations.asMap().entrySet()) {
			Set<String> uniqueNames  = new HashSet<>(entry.getValue());
			for (String name : uniqueNames) {
				tokenAnnoNamesList.add((tokenTextColIndex + 1), name);
			}
		}
	}

	@Override
	public Object getDataValue(SToken token, int columnIndex) {
		if (columnIndex < tokenTextColIndex) {
			return spanAnnoNamesList.get(columnIndex);
		}
		else if (columnIndex == tokenTextColIndex) {
			return graph.getText(token);
		}
		else if (columnIndex == (tokenTextColIndex + 1)) {
			int start = 0, end = 0;
			for (SRelation<?, ?> outRel : ((SNode) token).getOutRelations()) {
				if (outRel instanceof STextualRelation) {
					start = ((STextualRelation) outRel).getStart();
					end = ((STextualRelation) outRel).getEnd();
					return start + " - " + end;
				}
			}

		}
		else if (columnIndex > (tokenTextColIndex + 1)) {
			return tokenAnnoNamesList.get(columnIndex);
		}
		return null;
	}

	@Override
	public void setDataValue(SToken token, int columnIndex, Object newValue) {
	}

	@Override
	public int getColumnCount() {
		return colCount;
	}

	@Override
	public String getColumnProperty(int columnIndex) {
		return null;
	}

	@Override
	public int getColumnIndex(String propertyName) {
		return -1;
	}
}