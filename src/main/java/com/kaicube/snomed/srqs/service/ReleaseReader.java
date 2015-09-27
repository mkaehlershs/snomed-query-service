package com.kaicube.snomed.srqs.service;

import com.kaicube.snomed.srqs.domain.Concept;
import com.kaicube.snomed.srqs.exceptions.NotFoundException;
import com.kaicube.snomed.srqs.parser.secl.ExpressionConstraintBaseListener;
import com.kaicube.snomed.srqs.parser.secl.ExpressionConstraintLexer;
import com.kaicube.snomed.srqs.parser.secl.ExpressionConstraintParser;
import com.kaicube.snomed.srqs.service.dto.ConceptResult;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.Version;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ReleaseReader {

	private final IndexSearcher indexSearcher;
	private final QueryParser parser;

	public ReleaseReader(ReleaseStore releaseStore) throws IOException {
		indexSearcher = new IndexSearcher(DirectoryReader.open(releaseStore.getDirectory()));
		final Analyzer analyzer = releaseStore.createAnalyzer();
		parser = new QueryParser(Version.LUCENE_40, Concept.ID, analyzer);
		parser.setAllowLeadingWildcard(true);
	}

	protected ReleaseReader() {
		indexSearcher = null;
		parser = null;
	}

	public long getConceptCount() throws IOException {
		return indexSearcher.collectionStatistics(Concept.ID).docCount();
	}

	public ConceptResult retrieveConcept(String conceptId) throws IOException, NotFoundException {
		return getConceptResult(getConceptDocument(conceptId));
	}

	public List<ConceptResult> retrieveConcepts(String ecQuery) throws ParseException, IOException, NotFoundException {
		List<ConceptResult> concepts = new ArrayList<>();

		if (ecQuery != null && !ecQuery.isEmpty()) {
			final ELQuery query = parseQuery(ecQuery);
			final String focusConcept = query.getFocusConcept();
			if (query.isIncludeSelf()) {
				conditionalAdd(getConceptDocument(focusConcept), concepts, query.getAttributeName());
			}
			if (query.isDescendantOf()) {
			 	concepts.addAll(retrieveConceptDescendants(focusConcept, query.getAttributeName()));
			} else if (query.isAncestorOf()) {
				concepts.addAll(retrieveConceptAncestors(focusConcept, query.getAttributeName()));
			}
		}
		return concepts;
	}

	protected ELQuery parseQuery(String ecQuery) {
		final ExpressionConstraintLexer lexer = new ExpressionConstraintLexer(new ANTLRInputStream(ecQuery));
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		final ExpressionConstraintParser parser = new ExpressionConstraintParser(tokens);
		ParserRuleContext tree = parser.expressionconstraint();

		final ParseTreeWalker walker = new ParseTreeWalker();
		final ExpressionConstraintListener listener = new ExpressionConstraintListener();
		walker.walk(listener, tree);
		return listener.getElQuery();
	}

	public List<ConceptResult> retrieveConceptAncestors(String conceptId) throws ParseException, IOException, NotFoundException {
		return retrieveConceptAncestors(conceptId, null);
	}

	private List<ConceptResult> retrieveConceptAncestors(String conceptId, String attributeName) throws ParseException, IOException, NotFoundException {
		List<ConceptResult> concepts = new ArrayList<>();
		final String[] ancestorIds = getConceptDocument(conceptId).getValues(Concept.ANCESTOR);
		for (String ancestorId : ancestorIds) {
			conditionalAdd(getConceptDocument(ancestorId), concepts, attributeName);
		}
		return concepts;
	}

	public List<ConceptResult> retrieveConceptDescendants(String conceptId) throws ParseException, IOException {
		return retrieveConceptDescendants(conceptId, null);
	}

	private List<ConceptResult> retrieveConceptDescendants(String conceptId, String attributeName) throws ParseException, IOException {
		List<ConceptResult> concepts = new ArrayList<>();
		final Long idLong = new Long(conceptId);
		final TopDocs docs = indexSearcher.search(NumericRangeQuery.newLongRange(Concept.ANCESTOR, idLong, idLong, true, true), Integer.MAX_VALUE);
		for (ScoreDoc scoreDoc : docs.scoreDocs) {
			conditionalAdd(getConceptDocument(scoreDoc), concepts, attributeName);
		}
		return concepts;
	}

	private void conditionalAdd(Document document, List<ConceptResult> concepts, String attributeName) {
		if (attributeName == null || document.get(attributeName) != null) {
			concepts.add(getConceptResult(document));
		}
	}

	private Document getConceptDocument(ScoreDoc scoreDoc) throws IOException {
		return indexSearcher.doc(scoreDoc.doc);
	}

	private ConceptResult getConceptResult(Document document) {
		return new ConceptResult(document.get(Concept.ID), document.get(Concept.FSN));
	}

	private Document getConceptDocument(String conceptId) throws IOException, NotFoundException {
		final Long idLong = new Long(conceptId);
		final TopDocs docs = indexSearcher.search(NumericRangeQuery.newLongRange(Concept.ID, idLong, idLong, true, true), 1);
		if (docs.totalHits < 1) {
			throw new NotFoundException("Concept with id " + conceptId + " could not be found.");
		}
		return indexSearcher.doc(docs.scoreDocs[0].doc);
	}

	protected static final class ExpressionConstraintListener extends ExpressionConstraintBaseListener {

		private ELQuery elQuery;

		public ExpressionConstraintListener() {
			elQuery = new ELQuery();
		}

		@Override
		public void enterFocusconcept(ExpressionConstraintParser.FocusconceptContext ctx) {
			elQuery.setFocusConcept(ctx.conceptreference().conceptid().getPayload().getText());
		}

		@Override
		public void enterDescendantof(ExpressionConstraintParser.DescendantofContext ctx) {
			elQuery.descendantOf();
		}

		@Override
		public void enterDescendantorselfof(ExpressionConstraintParser.DescendantorselfofContext ctx) {
			elQuery.descendantOrSelfOf();
		}

		@Override
		public void enterAncestorof(ExpressionConstraintParser.AncestorofContext ctx) {
			elQuery.ancestorOf();
		}

		@Override
		public void enterAncestororselfof(ExpressionConstraintParser.AncestororselfofContext ctx) {
			elQuery.ancestorOrSelfOf();
		}

		@Override
		public void enterAttributename(ExpressionConstraintParser.AttributenameContext ctx) {
			elQuery.setAttributeName(ctx.conceptreference().conceptid().getPayload().getText());
		}

		@Override
		public void enterCompoundexpressionconstraint(ExpressionConstraintParser.CompoundexpressionconstraintContext ctx) {
			throwUnsupported();
		}

		@Override
		public void enterConjunctionrefinementset(ExpressionConstraintParser.ConjunctionrefinementsetContext ctx) {
			throwUnsupported("conjunctionRefinementSet");
		}

		@Override
		public void enterDisjunctionrefinementset(ExpressionConstraintParser.DisjunctionrefinementsetContext ctx) {
			throwUnsupported("disjunctionRefinementSet");
		}

		@Override
		public void enterConjunctionattributeset(ExpressionConstraintParser.ConjunctionattributesetContext ctx) {
			throwUnsupported("conjunctionAttributeSet");
		}

		@Override
		public void enterDisjunctionattributeset(ExpressionConstraintParser.DisjunctionattributesetContext ctx) {
			throwUnsupported("disjunctionAttributeSet");
		}

		@Override
		public void enterMemberof(ExpressionConstraintParser.MemberofContext ctx) {
			throwUnsupported("memberOf");
		}

		@Override
		public void enterWildcard(ExpressionConstraintParser.WildcardContext ctx) {
			throwUnsupported("wildcard");
		}

		@Override
		public void enterExpressioncomparisonoperator(ExpressionConstraintParser.ExpressioncomparisonoperatorContext ctx) {
			throwUnsupported("expressionComparisonOperator");
		}

		@Override
		public void enterNumericcomparisonoperator(ExpressionConstraintParser.NumericcomparisonoperatorContext ctx) {
			throwUnsupported("numericComparisonOperator");
		}

		@Override
		public void enterStringcomparisonoperator(ExpressionConstraintParser.StringcomparisonoperatorContext ctx) {
			throwUnsupported("stringComparisonOperator");
		}

		private void throwUnsupported() {
			throw new UnsupportedOperationException("This expression is not currently supported, please use a simpleExpressionConstraint.");
		}

		private void throwUnsupported(String feature) {
			throw new UnsupportedOperationException(feature + " is not currently supported.");
		}

		public ELQuery getElQuery() {
			return elQuery;
		}
	}
}
