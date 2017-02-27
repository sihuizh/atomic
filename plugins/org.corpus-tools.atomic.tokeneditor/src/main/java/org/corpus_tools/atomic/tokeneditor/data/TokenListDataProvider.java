/**
 * 
 */
package org.corpus_tools.atomic.tokeneditor.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.corpus_tools.salt.common.SDocumentGraph;
import org.corpus_tools.salt.common.SSpan;
import org.corpus_tools.salt.common.SSpanningRelation;
import org.corpus_tools.salt.common.STextualDS;
import org.corpus_tools.salt.common.SToken;
import org.corpus_tools.salt.core.SAnnotation;
import org.corpus_tools.salt.core.SFeature;
import org.corpus_tools.salt.core.SLayer;
import org.corpus_tools.salt.core.SNode;
import org.corpus_tools.salt.core.SRelation;
import org.eclipse.nebula.widgets.nattable.data.IColumnAccessor;
import org.eclipse.nebula.widgets.nattable.data.ISpanningDataProvider;
import org.eclipse.nebula.widgets.nattable.data.ListDataProvider;
import org.eclipse.nebula.widgets.nattable.layer.cell.DataCell;
import org.eclipse.swt.graphics.Point;

import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

/**
 * TODO Description
 *
 * @author Stephan Druskat <mail@sdruskat.net>
 *
 */
public class TokenListDataProvider implements ISpanningDataProvider {

	private static final Logger log = LogManager.getLogger(TokenListDataProvider.class);

	private final SDocumentGraph graph;

	private final Multimap<String, String> uniqueSpanAnnotations = ArrayListMultimap.create();
	private final Multimap<String, String> uniqueTokenAnnotations = ArrayListMultimap.create();
	private final Multimap<String, String> uniqueAnnotations = ArrayListMultimap.create();

	private final Map<Point, Object> values = new HashMap<>();

	// ############################ VALUES FROM NatTable example! Check usage //
	// FIXME
	private static final int BLOCK_SIZE = 4;
	private static final int CELL_SPAN = 2;

	/**
	 * @param list
	 * @param columnAccessor
	 */
	public TokenListDataProvider(SDocumentGraph graph, IColumnAccessor<SToken> columnAccessor) {
		this.graph = graph;
		parseSalt();
	}

	/**
	 * // TODO Add description
	 * 
	 * @return
	 */
	public LinkedHashMap<String, ArrayList<?>> parseSalt(List<String> annotationNames) {
		STextualDS text = graph.getTextualDSs().get(0);
		List<String> annotationsNames = computeAnnotations();

		// only look at annotations which were defined by the user
		LinkedHashMap<String, ArrayList<?>> rowsByAnnotation = new LinkedHashMap<>();

		// annotationNames = computeAnnotations();

		for (String anno : annotationNames) {
			rowsByAnnotation.put(anno, new ArrayList<Object>());
		}

		AtomicInteger eventCounter = new AtomicInteger();

		for (SSpan span : graph.getSpans()) {
			// FIXME For more than 1 STextualDS
			addAnnotationsForNode(span, graph, startTokenIndex, endTokenIndex, eventCounter, rowsByAnnotation, true);
		} // end for each span

		for (SToken tok : graph.getTokens()) {
				addAnnotationsForNode(tok, graph, startTokenIndex, endTokenIndex, eventCounter, rowsByAnnotation, true);
		}

		// 2. merge rows when possible
		for (Map.Entry<String, ArrayList<?>> e : rowsByAnnotation.entrySet()) {
			mergeAllRowsIfPossible(e.getValue());
		}

		// 3. sort events on one row by left token index
		for (Map.Entry<String, ArrayList<?>> e : rowsByAnnotation.entrySet()) {
			for (Row r : e.getValue()) {
				sortEventsByTokenIndex(r);
			}
		}

		// 4. split up events if they cover islands
		for (Map.Entry<String, ArrayList<?>> e : rowsByAnnotation.entrySet()) {
			for (Row r : e.getValue()) {
				splitRowsOnIslands(r, graph, text, startTokenIndex, endTokenIndex);
			}
		}

		// 5. split up events if they have gaps
		for (Map.Entry<String, ArrayList<?>> e : rowsByAnnotation.entrySet()) {
			for (Row r : e.getValue()) {
				splitRowsOnGaps(r, graph, startTokenIndex, endTokenIndex);
			}
		}

		return rowsByAnnotation;
	}

	/// **
	// * // TODO Add description
	// *
	// * @param node
	// * @param graph
	// * @param startTokenIndex
	// * @param endTokenIndex
	// * @param pdfController
	// * @param pageNumberHelper
	// * @param eventCounter
	// * @param rowsByAnnotation
	// * @param addMatch
	// * @param mediaLayer
	// * @param replaceValueWithMediaIcon
	// */
	// private static void addAnnotationsForNode(SNode node, long
	/// startTokenIndex, long endTokenIndex,
	// LinkedHashMap<String, ArrayList<?>> rowsByAnnotation,
	// boolean addMatch)
	// {
	//
	// List<String> matchedAnnos = new ArrayList<>();
	// SFeature featMatchedAnnos = graph.getFeature(ANNIS_NS,
	/// FEAT_MATCHEDANNOS);
	// if(featMatchedAnnos != null)
	// {
	// matchedAnnos = Splitter.on(',').trimResults()
	// .splitToList(featMatchedAnnos.getValue_STEXT());
	// }
	// // check if the span is a matched node
	// SFeature featMatched = node.getFeature(ANNIS_NS, FEAT_MATCHEDNODE);
	// Long matchRaw = featMatched == null ? null : featMatched.
	// getValue_SNUMERIC();
	//
	// String matchedQualifiedAnnoName = "";
	// if(matchRaw != null && matchRaw <= matchedAnnos.size())
	// {
	// matchedQualifiedAnnoName = matchedAnnos.get((int) ((long) matchRaw)-1);
	// }
	//
	//
	// // calculate the left and right values of a span
	// // TODO: howto get these numbers with Salt?
	// RelannisNodeFeature feat = (RelannisNodeFeature) node.
	// getFeature(ANNIS_NS, FEAT_RELANNIS_NODE).getValue();
	//
	// long leftLong = feat.getLeftToken();
	// long rightLong = feat.getRightToken();
	//
	// leftLong = clip(leftLong, startTokenIndex, endTokenIndex);
	// rightLong = clip(rightLong, startTokenIndex, endTokenIndex);
	//
	// int left = (int) (leftLong - startTokenIndex);
	// int right = (int) (rightLong - startTokenIndex);
	//
	// for (SAnnotation anno : node.getAnnotations())
	// {
	// ArrayList<Row> rows = rowsByAnnotation.get(anno.getQName());
	// if (rows == null)
	// {
	// // try again with only the name
	// rows = rowsByAnnotation.get(anno.getName());
	// }
	// if (rows != null)
	// {
	// // only do something if the annotation was defined before
	//
	// // 1. give each annotation of each span an own row
	// Row r = new Row();
	//
	// String id = "event_" + eventCounter.incrementAndGet();
	// GridEvent event = new GridEvent(id, left, right,
	// anno.getValue_STEXT());
	// event.setTooltip(Helper.getQualifiedName(anno));
	//
	// if(addMatch && matchRaw != null)
	// {
	// long match = matchRaw;
	//
	// if(matchedQualifiedAnnoName.isEmpty())
	// {
	// // always set the match when there is no matched annotation at all
	// event.setMatch(match);
	// }
	// // check if the annotation also matches
	// else if(matchedQualifiedAnnoName.equals(anno.getQName()))
	// {
	// event.setMatch(match);
	// }
	//
	// }
	// if(node instanceof SSpan)
	// {
	// // calculate overlapped SToken
	//
	// List<? extends SRelation<? extends SNode, ? extends SNode>> outEdges =
	/// graph.getOutRelations(node.getId());
	// if (outEdges != null)
	// {
	// for (SRelation<? extends SNode, ? extends SNode> e : outEdges)
	// {
	// if (e instanceof SSpanningRelation)
	// {
	// SSpanningRelation spanRel = (SSpanningRelation) e;
	//
	// SToken tok = spanRel.getTarget();
	// event.getCoveredIDs().add(tok.getId());
	//
	// // get the STextualDS of this token and add it to the event
	// String textID = getTextID(tok, graph);
	// if(textID != null)
	// {
	// event.setTextID(textID);
	// }
	// }
	// }
	// } // end if span has out edges
	// }
	// else if(node instanceof SToken)
	// {
	// event.getCoveredIDs().add(node.getId());
	// // get the STextualDS of this token and add it to the event
	// String textID = getTextID((SToken) node, graph);
	// if(textID != null)
	// {
	// event.setTextID(textID);
	// }
	// }
	//
	//
	// // try to get time annotations
	// if(mediaLayer == null || mediaLayer.contains(anno.getQName()))
	// {
	//
	// double[] startEndTime = TimeHelper.getOverlappedTime(node);
	// if (startEndTime.length == 1)
	// {
	// if (replaceValueWithMediaIcon)
	// {
	// event.setValue(" ");
	// event.setTooltip("play excerpt " + event.getStartTime());
	// }
	// event.setStartTime(startEndTime[0]);
	// }
	// else if (startEndTime.length == 2)
	// {
	// event.setStartTime(startEndTime[0]);
	// event.setEndTime(startEndTime[1]);
	// if (replaceValueWithMediaIcon)
	// {
	// event.setValue(" ");
	// event.setTooltip("play excerpt " + event.getStartTime() + "-"
	// + event.getEndTime());
	// }
	// }
	//
	// }
	//
	// r.addEvent(event);
	// rows.add(r);
	//
	// if (pdfController != null &&
	// pdfController.sizeOfRegisterdPDFViewer() > 0)
	// {
	// String page = pageNumberHelper.getPageFromAnnotation(node);
	// if (page != null)
	// {
	// event.setPage(page);
	// }
	// }
	// }
	// } // end for each annotation of span
	// }

	/**
	 * // TODO Add description
	 * 
	 * @param startIndex
	 * @param endIndex
	 * @return
	 */
	private LinkedHashMap<String, ArrayList<?>> computeAnnotations(long startIndex, long endIndex) {
		List<String> annos = new LinkedList<>();

		annos.addAll(computeDisplayAnnotations(SSpan.class));
		annos.addAll(computeDisplayAnnotations(SToken.class));
		LinkedHashMap<String, ArrayList<?>> rowsByAnnotation = parseSalt(annos);

		return rowsByAnnotation;
	}

	// /**
	// * // TODO Add description
	// *
	// * @param type
	// * @return
	// */
	// public List<String> computeDisplayAnnotations(Class<? extends SNode>
	// type) {
	// Set<String> annoPool = SToken.class.isAssignableFrom(type) ?
	// getAnnotationLevelSet(null, type) : getAnnotationLevelSet("default",
	// type);
	// List<String> annos = new LinkedList<>(annoPool);
	//
	// String annosConfiguration = "annos";
	// if (annosConfiguration != null && annosConfiguration.trim().length() > 0)
	// {
	// String[] split = annosConfiguration.split(",");
	// annos.clear();
	// for (String s : split) {
	// s = s.trim();
	// // is regular expression?
	// if (s.startsWith("/") && s.endsWith("/")) {
	// // go over all remaining items in our pool of all
	// // annotations and
	// // check if they match
	// Pattern regex = Pattern.compile(StringUtils.strip(s, "/"));
	//
	// LinkedList<String> matchingAnnos = new LinkedList<>();
	// for (String a : annoPool) {
	// if (regex.matcher(a).matches()) {
	// matchingAnnos.add(a);
	// }
	// }
	//
	// annos.addAll(matchingAnnos);
	// annoPool.removeAll(matchingAnnos);
	//
	// } else {
	// annos.add(s);
	// annoPool.remove(s);
	// }
	// }
	// }
	//
	// // filter already found annotation names by regular expression
	// // if this was given as mapping
	// String regexFilterRaw = "annos_regex";
	// if (regexFilterRaw != null) {
	// try {
	// Pattern regexFilter = Pattern.compile(regexFilterRaw);
	// ListIterator<String> itAnnos = annos.listIterator();
	// while (itAnnos.hasNext()) {
	// String a = itAnnos.next();
	// // remove entry if not matching
	// if (!regexFilter.matcher(a).matches()) {
	// itAnnos.remove();
	// }
	// }
	// } catch (PatternSyntaxException ex) {
	// log.warn("invalid regular expression in mapping for grid visualizer",
	// ex);
	// }
	// }
	// return annos;
	// }

	/**
	 * // TODO Add description
	 * 
	 * @param graph
	 * @param namespace
	 * @param type
	 * @return
	 */
	private Set<String> getAnnotationLevelSet(String namespace, Class<? extends SNode> type) {
		Set<String> result = new TreeSet<>();

		if (graph != null) {
			List<? extends SNode> nodes;
			// catch most common cases directly
			if (SSpan.class == type) {
				nodes = graph.getSpans();
			} else if (SToken.class == type) {
				nodes = graph.getTokens();
			} else {
				nodes = graph.getNodes();
			}
			if (nodes != null) {
				for (SNode n : nodes) {
					if (type.isAssignableFrom(n.getClass())) {
						for (SLayer layer : n.getLayers()) {
							if (namespace == null || namespace.equals(layer.getName())) {
								for (SAnnotation anno : n.getAnnotations()) {
									result.add(anno.getQName());
								}
								// we got all annotations of this node, jump to
								// next node
								break;
							} // end if namespace equals layer name
						} // end for each layer
					}
				} // end for each node
			}
		}
		return result;
	}

	@Override
	public Object getDataValue(int columnIndex, int rowIndex) {
		// Point point = new Point(columnIndex, rowIndex);
		// if (this.values.containsKey(point)) {
		// return this.values.get(point);
		// } else {
		// log.warn("Data value is not in list!");
		// return null;
		// }
		return null;
	}

	@Override
	public void setDataValue(int columnIndex, int rowIndex, Object newValue) {
		this.values.put(new Point(columnIndex, rowIndex), newValue);
	}

	@Override
	public int getColumnCount() {
		/*
		 * Find out how many columns we need! NOTE: Rows will be tokens, columns
		 * will be annotations, etc.
		 */
		// for (SSpan span : graph.getSpans()) {
		// for (SAnnotation annotation : span.getAnnotations()) {
		// uniqueSpanAnnotations.put(annotation.getNamespace(),
		// annotation.getName());
		// }
		// }
		for (SToken token : graph.getTokens()) {
			for (SAnnotation annotation : token.getAnnotations()) {
				uniqueTokenAnnotations.put(annotation.getNamespace(), annotation.getName());
			}
		}
		// uniqueAnnotations.putAll(uniqueSpanAnnotations);
		// uniqueAnnotations.putAll(uniqueTokenAnnotations);
		int colCount = 2; // Token text and token indices
		for (Entry<String, Collection<String>> entry : uniqueTokenAnnotations.asMap().entrySet()) {
			Set<String> uniqueNames = new HashSet<>(entry.getValue());
			for (String name : uniqueNames) {
				colCount++;
			}
		}
		// tokenTextColIndex = uniqueSpanAnnotations.asMap().values().size() +
		// 1;
		// System.err.println(tokenTextColIndex + " span annos");
		return colCount;
		// return 2;
	}

	@Override
	public int getRowCount() {
		return graph.getTokens().size();
	}

	/**
	 * FIXME Change to work with my thing!
	 * 
	 * @param columnPosition
	 * @param rowPosition
	 * @return
	 */
	@Override
	public DataCell getCellByPosition(int columnPosition, int rowPosition) {
		int columnBlock = columnPosition / BLOCK_SIZE;
		int rowBlock = rowPosition / BLOCK_SIZE;

		boolean isSpanned = isEven(columnBlock + rowBlock) && (columnPosition % BLOCK_SIZE) < CELL_SPAN
				&& (rowPosition % BLOCK_SIZE) < CELL_SPAN;
		int columnSpan = isSpanned ? CELL_SPAN : 1;
		int rowSpan = isSpanned ? CELL_SPAN : 1;

		int cellColumnPosition = columnPosition;
		int cellRowPosition = rowPosition;

		if (isSpanned) {
			cellColumnPosition -= columnPosition % BLOCK_SIZE;
			cellRowPosition -= rowPosition % BLOCK_SIZE;
		}

		return new DataCell(cellColumnPosition, cellRowPosition, columnSpan, rowSpan);
	}

	/**
	 * FIXME REMOVE!!!
	 * 
	 * @param i
	 * @return
	 */
	private boolean isEven(int i) {
		return i % 2 == 0;
	}

	// /**
	// * TODO: Description
	// *
	// * @param graph2
	// */
	// private void initialize() {
	// /*
	// * Find out how many columns we need!
	// * NOTE: Rows will be tokens, columns will be annotations, etc.
	// */
	// for (SSpan span : graph.getSpans()) {
	// for (SAnnotation annotation : span.getAnnotations()) {
	// uniqueSpanAnnotations.put(annotation.getNamespace(),
	// annotation.getName());
	// }
	// }
	// for (SToken token : graph.getTokens()) {
	// for (SAnnotation annotation : token.getAnnotations()) {
	// uniqueTokenAnnotations.put(annotation.getNamespace(),
	// annotation.getName());
	// }
	// }
	// uniqueAnnotations.putAll(uniqueSpanAnnotations);
	// uniqueAnnotations.putAll(uniqueTokenAnnotations);
	// colCount = 2; // Token text and token indices
	// for (Entry<String, Collection<String>> entry :
	// uniqueAnnotations.asMap().entrySet()) {
	// Set<String> uniqueNames = new HashSet<>(entry.getValue());
	// for (String name : uniqueNames) {
	// colCount++;
	// }
	// }
	// tokenTextColIndex = uniqueSpanAnnotations.asMap().values().size() + 1;
	// System.err.println(tokenTextColIndex + " span annos");
	// }

}
