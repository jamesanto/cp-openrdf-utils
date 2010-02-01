/*
 * Copyright (c) 2009-2010 Clark & Parsia, LLC. <http://www.clarkparsia.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.clarkparsia.openrdf;

import org.openrdf.repository.base.RepositoryWrapper;

import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.RepositoryResult;

import org.openrdf.model.Resource;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.Statement;
import org.openrdf.model.Graph;

import org.openrdf.model.vocabulary.RDF;
import org.openrdf.model.vocabulary.RDFS;

import org.openrdf.model.impl.GraphImpl;

import org.openrdf.query.TupleQueryResult;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.BindingSet;

import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.GraphQueryResult;

import org.openrdf.query.impl.TupleQueryResultImpl;
import org.openrdf.rio.RDFParseException;
import org.openrdf.rio.RDFFormat;

import org.apache.log4j.Logger;
import org.apache.log4j.LogManager;

import java.util.Collection;
import java.util.HashSet;
import java.util.Collections;
import java.io.IOException;
import java.io.InputStream;

import com.clarkparsia.utils.collections.CollectionUtil;
import static com.clarkparsia.utils.collections.CollectionUtil.transform;
import com.clarkparsia.utils.Function;
import com.clarkparsia.utils.FunctionUtil;
import static com.clarkparsia.utils.FunctionUtil.compose;

import static com.clarkparsia.openrdf.OpenRdfUtil.close;
import static com.clarkparsia.openrdf.OpenRdfUtil.getQueryString;
import static com.clarkparsia.openrdf.OpenRdfUtil.asGraph;
import com.clarkparsia.openrdf.util.IterationIterator;

import info.aduna.iteration.EmptyIteration;
import info.aduna.iteration.CloseableIteration;

/**
 * <p></p>
 *
 * @author Michael Grove
 */
public class ExtRepository extends RepositoryWrapper {
	private static Logger LOGGER = LogManager.getLogger("com.clarkparsia.openrdf");
	
	public ExtRepository() {
	}

	public ExtRepository(final Repository theRepository) {
		super(theRepository);
	}

	/**
	 * Return a graph which describes the given URI
	 * @param theURI the URI to describe
	 * @return the graph which describes the URI
	 */
	public Graph describe(URI theURI) {
		Graph aGraph = new GraphImpl();

		RepositoryConnection aConn = null;

		try {
			aConn = getConnection();

			aGraph.addAll(CollectionUtil.set(new IterationIterator<Statement>(getStatements(theURI, null, null))));
		}
		catch (Exception ex) {
			LOGGER.error(ex);
		}
		finally {
			close(aConn);
		}

		return aGraph;
	}

	/**
	 * Return an Iterable over the statements in this Repository which match the given spo pattern.
	 * @param theSubj the subject to search for, or null for any
	 * @param thePred the predicate to search for, or null for any
	 * @param theObj the object to search for, or null for any
	 * @return an Iterable over the matching statements
	 */
	public RepositoryResult<Statement> getStatements(Resource theSubj, URI thePred, Value theObj) {
		RepositoryConnection aConn = null;
		try {
			aConn = getConnection();

			return aConn.getStatements(theSubj, thePred, theObj, true);
		}
		catch (Exception ex) {
			close(aConn);

			LOGGER.error(ex);

			return new RepositoryResult<Statement>(emptyStatementIteration());
		}
	}

	private CloseableIteration<Statement, RepositoryException> emptyStatementIteration() {
		return new EmptyIteration<Statement, RepositoryException>();
	}

	private CloseableIteration<BindingSet, QueryEvaluationException> emptyQueryIteration() {
		return new EmptyIteration<BindingSet, QueryEvaluationException>();
	}

	private TupleQueryResult emptyQueryResults() {
		return new TupleQueryResultImpl(Collections.<String>emptyList(), emptyQueryIteration());
	}

	public TupleQueryResult selectQuery(SesameQuery theQuery) throws RepositoryException, MalformedQueryException, QueryEvaluationException {
		return selectQuery(theQuery.getLanguage(), theQuery.getQueryString()); 
	}

	public TupleQueryResult selectQuery(QueryLanguage theLang, String theQuery) throws RepositoryException, MalformedQueryException, QueryEvaluationException {
		RepositoryConnection aConn = null;
		try {
			aConn = getConnection();
			return aConn.prepareTupleQuery(theLang, theQuery).evaluate();
		}
		catch (RepositoryException e) {
			close(aConn);
			throw e;
		}
	}

	public ExtGraph constructQuery(SesameQuery theQuery) throws RepositoryException, MalformedQueryException, QueryEvaluationException {
		return constructQuery(theQuery.getLanguage(), theQuery.getQueryString());
	}

	public ExtGraph constructQuery(QueryLanguage theLang, String theQuery) throws RepositoryException, MalformedQueryException, QueryEvaluationException {
		RepositoryConnection aConn = null;

		try {
			aConn = getConnection();

			ExtGraph aGraph = new ExtGraph();

			GraphQueryResult aResult = aConn.prepareGraphQuery(theLang, theQuery).evaluate();
			while (aResult.hasNext()) {
				aGraph.add(aResult.next());
			}

			return aGraph;
		}
		finally {
			close(aConn);
		}
	}
	/**
	 * Read data in the specified format from the stream and insert it into this Repository
	 * @param theStream the stream to read data from
	 * @param theFormat the format the data is in
	 * @throws IOException thrown if there is an error while reading from the stream
	 * @throws RDFParseException thrown if the data cannot be parsed into the specified format
	 */
	public void read(InputStream theStream, RDFFormat theFormat) throws IOException, RDFParseException {
		OpenRdfIO.addData(this, theStream, theFormat);
	}

	/**
	 * List all the subjects which have the given predicate and object.
	 * @param thePredicate the predicate to search for, or null for any predicate
	 * @param theObject the object to search for, or null for any object
	 * @return the list of subjects who have properties matching the po pattern.
	 */
	public Collection<Resource> getSubjects(URI thePredicate, Value theObject) {
		String aQuery = "select uri from {uri} " + (thePredicate == null ? "p" : getQueryString(thePredicate)) + " {" + (theObject == null ? "o" : getQueryString(theObject)) + "}";

		RepositoryConnection aConn = null;

		try {
			Collection<Resource> aSubjects = new HashSet<Resource>();

			aConn = getConnection();

			TupleQueryResult aResult = aConn.prepareTupleQuery(QueryLanguage.SERQL, aQuery).evaluate();

			while (aResult.hasNext()) {
				aSubjects.add((Resource) aResult.next().getValue("uri"));
			}

			aResult.close();

			return aSubjects;
		}
		catch (Exception e) {
			LOGGER.error(e);

			return Collections.emptySet();
		}
		finally {
			close(aConn);
		}
	}

	/**
	 * Return the value of the property on the resource
	 * @param theSubj the subject
	 * @param thePred the property to get from the subject
	 * @return the first value of the property for the resource, or null if it does not have the specified property or does not exist.
	 */
	public Value getValue(Resource theSubj, URI thePred) {
        Iterable<Value> aIter = getValues(theSubj, thePred);

        if (aIter.iterator().hasNext()) {
            return aIter.iterator().next();
        }
        else {
			return null;
		}
	}

	/**
	 * Return the superclasses of the given resource
	 * @param theRes the resource
	 * @return the resource's superclasses
	 */
	public Iterable<Resource> getSuperclasses(Resource theRes) {
		return transform(new IterationIterator<Statement>(getStatements(theRes, RDFS.SUBCLASSOF, null)),
						 compose(new Function<Statement, Value>() {public Value apply(Statement theStmt) { return theStmt.getObject(); } },
								 new FunctionUtil.Cast<Value, Resource>(Resource.class)));
	}

	/**
	 * Return the values of the subject for the given property
	 * @param theSubj the subject
	 * @param thePred the property of the subject to get values for
	 * @return an iterable set of values of the property
	 */
	public Iterable<Value> getValues(Resource theSubj, URI thePred) {
		if (theSubj == null || thePred == null) {
			return Collections.emptySet();
		}

        try {
            String aQuery = "select value from {"+ getQueryString(theSubj)+"} <"+thePred+"> {value}";

            TupleQueryResult aTable = selectQuery(QueryLanguage.SERQL, aQuery);

            return transform(new IterationIterator<BindingSet>(aTable), new Function<BindingSet, Value>() {
				public Value apply(final BindingSet theIn) {
					return theIn.getValue("value");
				}
			});
        }
        catch (Exception ex) {
            //System.err.println("Error getting value for "+theSubj+", "+thePred);
        }

        return new HashSet<Value>();
	}

	/**
	 * Return whether or not the given resource represents an rdf:List.
	 * @param theRes the resource to inspect
	 * @return true if it is an rdf:List, false otherwise
	 */
	public boolean isList(Resource theRes) {
		return theRes.equals(RDF.NIL) || getValue(theRes, RDF.FIRST) != null;
	}

	/**
	 * Add the statements to the repository
	 * @param theStatement the statement(s) to add
	 * @throws RepositoryException thrown if there is an error while adding
	 */
	public void add(Statement... theStatement) throws RepositoryException {
		addGraph(asGraph(theStatement));
	}

	public void addGraph(final Graph theGraph) throws RepositoryException {
		RepositoryConnection aConn = null;

		try {
			aConn = getConnection();

			aConn.add(theGraph);
		}
		finally {
			close(aConn);
		}
	}

	public void removeGraph(final Graph theGraph) throws RepositoryException {
		RepositoryConnection aConn = null;

		try {
			aConn = getConnection();

			aConn.remove(theGraph);
		}
		finally {
			close(aConn);
		}
	}
}