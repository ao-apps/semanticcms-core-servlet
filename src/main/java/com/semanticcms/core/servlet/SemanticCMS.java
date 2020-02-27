/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2014, 2015, 2016, 2019, 2020  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of semanticcms-core-servlet.
 *
 * semanticcms-core-servlet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * semanticcms-core-servlet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with semanticcms-core-servlet.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.semanticcms.core.servlet;

import com.aoindustries.servlet.PropertiesUtils;
import com.aoindustries.servlet.http.Dispatcher;
import com.aoindustries.util.WrappedException;
import com.aoindustries.xml.XmlUtils;
import com.semanticcms.core.model.Book;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.model.ParentRef;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import javax.servlet.http.HttpServletRequest;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * The SemanticCMS application context.
 * 
 * TODO: Consider custom EL resolver for this variable: http://stackoverflow.com/questions/5016965/how-to-add-a-custom-variableresolver-in-pure-jsp
 */
public class SemanticCMS {

	// <editor-fold defaultstate="collapsed" desc="Singleton Instance (per application)">

	@WebListener("Exposes the application context as an application-scope SemanticCMS instance named \"" + APPLICATION_ATTRIBUTE + "\".")
	public static class Initializer implements ServletContextListener {

		private SemanticCMS instance;

		@Override
		public void contextInitialized(ServletContextEvent event) {
			instance = getInstance(event.getServletContext());
		}

		@Override
		public void contextDestroyed(ServletContextEvent event) {
			if(instance != null) {
				instance.destroy();
				instance = null;
			}
			event.getServletContext().removeAttribute(APPLICATION_ATTRIBUTE);
		}
	}

	public static final String APPLICATION_ATTRIBUTE = "semanticCMS";

	/**
	 * Gets the SemanticCMS instance, creating it if necessary.
	 */
	public static SemanticCMS getInstance(ServletContext servletContext) {
		try {
			SemanticCMS semanticCMS = (SemanticCMS)servletContext.getAttribute(APPLICATION_ATTRIBUTE);
			if(semanticCMS == null) {
				// TODO: Support custom implementations via context-param?
				semanticCMS = new SemanticCMS(servletContext);
				servletContext.setAttribute(APPLICATION_ATTRIBUTE, semanticCMS);
			}
			return semanticCMS;
		} catch(IOException | SAXException | ParserConfigurationException | XPathExpressionException e) {
			throw new WrappedException(e);
		}
	}

	private final ServletContext servletContext;

	protected SemanticCMS(ServletContext servletContext) throws IOException, SAXException, ParserConfigurationException, XPathExpressionException {
		this.servletContext = servletContext;
		this.demoMode = Boolean.parseBoolean(servletContext.getInitParameter(DEMO_MODE_INIT_PARAM));
		int numProcessors = Runtime.getRuntime().availableProcessors();
		this.concurrentSubrequests =
			numProcessors > 1
			&& Boolean.parseBoolean(servletContext.getInitParameter(CONCURRENT_SUBREQUESTS_INIT_PARAM))
		;
		this.rootBook = initBooks();
		this.executors = new Executors();
	}

	/**
	 * Called when the context is shutting down.
	 */
	protected void destroy() {
	}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="Demo Mode">
	private static final String DEMO_MODE_INIT_PARAM = "com.semanticcms.core.servlet.SemanticCMS.demoMode";

	private final boolean demoMode;

	/**
	 * When true, a cursory attempt will be made to hide sensitive information for demo mode.
	 */
	public boolean getDemoMode() {
		return demoMode;
	}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="Books">
	// See https://docs.oracle.com/javase/tutorial/jaxp/dom/validating.html
	private static final String BOOKS_XML_RESOURCE = "/WEB-INF/books.xml";
	private static final String BOOKS_XML_SCHEMA_RESOURCE = "books-1.0.xsd";

	private static final String MISSING_BOOK_TAG = "missingBook";
	private static final String BOOK_TAG = "book";
	private static final String PARENT_TAG = "parent";
	private static final String ROOT_BOOK_ATTRIBUTE = "rootBook";

	private final Map<String,Book> books = new LinkedHashMap<>();
	private final Set<String> missingBooks = new LinkedHashSet<>();
	private final Book rootBook;

	private Book initBooks() throws IOException, SAXException, ParserConfigurationException, XPathExpressionException {
		Document booksXml;
		{
			InputStream schemaIn = SemanticCMS.class.getResourceAsStream(BOOKS_XML_SCHEMA_RESOURCE);
			if(schemaIn == null) throw new IOException("Schema not found: " + BOOKS_XML_SCHEMA_RESOURCE);
			try {
				DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
				dbf.setNamespaceAware(true);
				dbf.setValidating(true);
				dbf.setAttribute("http://java.sun.com/xml/jaxp/properties/schemaLanguage", XMLConstants.W3C_XML_SCHEMA_NS_URI);
				dbf.setAttribute("http://java.sun.com/xml/jaxp/properties/schemaSource", schemaIn);
				DocumentBuilder db = dbf.newDocumentBuilder();
				InputStream booksXmlIn = servletContext.getResource(BOOKS_XML_RESOURCE).openStream();
				if(booksXmlIn == null) throw new IOException(BOOKS_XML_RESOURCE + " not found");
				try {
					booksXml = db.parse(booksXmlIn);
				} finally {
					booksXmlIn.close();
				}
			} finally {
				schemaIn.close();
			}
		}
		org.w3c.dom.Element booksElem = booksXml.getDocumentElement();
		// Load missingBooks
		for(org.w3c.dom.Element missingBookElem : XmlUtils.iterableChildElementsByTagName(booksElem, MISSING_BOOK_TAG)) {
			String name = missingBookElem.getAttribute("name");
			if(!missingBooks.add(name)) throw new IllegalStateException(BOOKS_XML_RESOURCE+ ": Duplicate value for \"" + MISSING_BOOK_TAG + "\": " + name);
		}
		// Load books
		String rootBookName = booksElem.getAttribute(ROOT_BOOK_ATTRIBUTE);
		if(rootBookName == null || rootBookName.isEmpty()) throw new IllegalStateException(BOOKS_XML_RESOURCE + ": \"" + ROOT_BOOK_ATTRIBUTE + "\" not found");
		for(org.w3c.dom.Element bookElem : XmlUtils.iterableChildElementsByTagName(booksElem, BOOK_TAG)) {
			String name = bookElem.getAttribute("name");
			if(missingBooks.contains(name)) throw new IllegalStateException(BOOKS_XML_RESOURCE + ": Book also listed in \"" + MISSING_BOOK_TAG+ "\": " + name);
			Set<ParentRef> parentRefs = new LinkedHashSet<>();
			for(org.w3c.dom.Element parentElem : XmlUtils.iterableChildElementsByTagName(bookElem, PARENT_TAG)) {
				String parentBookName = parentElem.getAttribute("book");
				String parentPage = parentElem.getAttribute("page");
				String parentShortTitle = parentElem.hasAttribute("shortTitle") ? parentElem.getAttribute("shortTitle") : null;
				Book parentBook = books.get(parentBookName);
				if(parentBook == null) {
					throw new IllegalStateException(BOOKS_XML_RESOURCE + ": parent book not found (loading order currently matters): " + parentBookName);
				}
				parentRefs.add(new ParentRef(new PageRef(parentBook, parentPage), parentShortTitle));
			}
			if(name.equals(rootBookName)) {
				if(!parentRefs.isEmpty()) {
					throw new IllegalStateException(BOOKS_XML_RESOURCE + ": \"" + ROOT_BOOK_ATTRIBUTE + "\" may not have any parents: " + rootBookName);
				}
			} else {
				if(parentRefs.isEmpty()) {
					throw new IllegalStateException(BOOKS_XML_RESOURCE + ": Non-root books must have at least one parent: " + name);
				}
			}
			books.put(
				name,
				new Book(
					name,
					bookElem.getAttribute("cvsworkDirectory"),
					Boolean.valueOf(bookElem.getAttribute("allowRobots")),
					parentRefs,
					PropertiesUtils.loadFromResource(servletContext, ("/".equals(name) ? "" : name) + "/book.properties")
				)
			);
		}

		// Load rootBook
		Book newRootBook = books.get(rootBookName);
		if(newRootBook == null) throw new AssertionError();

		// Successful book load
		return newRootBook;
	}

	public Map<String,Book> getBooks() {
		return Collections.unmodifiableMap(books);
	}

	public Set<String> getMissingBooks() {
		return Collections.unmodifiableSet(missingBooks);
	}

	/**
	 * Gets the root book as configured in /WEB-INF/books.properties
	 */
	public Book getRootBook() {
		return rootBook;
	}

	/**
	 * Gets the book for the provided context-relative servlet path or <code>null</code> if no book configured at that path.
	 * The book with the longest prefix match is used.
	 * The servlet path must begin with a slash (/).
	 */
	public Book getBook(String servletPath) {
		if(servletPath.charAt(0) != '/') throw new IllegalArgumentException("Invalid servletPath: " + servletPath);
		Book longestPrefixBook = null;
		int longestPrefixLen = -1;
		for(Book book : getBooks().values()) {
			String prefix = book.getPathPrefix();
			int prefixLen = prefix.length();
			if(
				prefixLen > longestPrefixLen
				&& servletPath.startsWith(prefix)
			) {
				longestPrefixBook = book;
				longestPrefixLen = prefixLen;
			}
		}
		return longestPrefixBook;
	}

	/**
	 * Gets the book for the provided request or <code>null</code> if no book configured at the current request path.
	 */
	public Book getBook(HttpServletRequest request) {
		return getBook(Dispatcher.getCurrentPagePath(request));
	}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="Views">
	/**
	 * The parameter name used for views.
	 */
	public static final String VIEW_PARAM = "view";

	/**
	 * The default view is the content view and will have the empty view name.
	 */
	public static final String DEFAULT_VIEW_NAME = "content";

	private static class ViewsLock {}
	private final ViewsLock viewsLock = new ViewsLock();

	/**
	 * The views by name in order added.
	 */
	private final Map<String,View> viewsByName = new LinkedHashMap<>();

	private static final Set<View.Group> viewGroups = Collections.unmodifiableSet(EnumSet.allOf(View.Group.class));

	/**
	 * Gets all view groups.
	 */
	public Set<View.Group> getViewGroups() {
		return viewGroups;
	}

	/**
	 * Gets the views in order added.
	 */
	public Map<String,View> getViewsByName() {
		return Collections.unmodifiableMap(viewsByName);
	}

	/**
	 * The views in order.
	 */
	private final SortedSet<View> views = new TreeSet<>();

	/**
	 * Gets the views, ordered by view group then display.
	 *
	 * @see  View#compareTo(com.semanticcms.core.servlet.View)
	 */
	public SortedSet<View> getViews() {
		return Collections.unmodifiableSortedSet(views);
	}

	/**
	 * Registers a new view.
	 *
	 * @throws  IllegalStateException  if a view is already registered with the name.
	 */
	public void addView(View view) throws IllegalStateException {
		String name = view.getName();
		synchronized(viewsLock) {
			if(viewsByName.containsKey(name)) throw new IllegalStateException("View already registered: " + name);
			if(viewsByName.put(name, view) != null) throw new AssertionError();
			if(!views.add(view)) throw new AssertionError();
		}
	}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="Components">
	/**
	 * The components that are currently registered.
	 */
	private final List<Component> components = new CopyOnWriteArrayList<>();

	/**
	 * Gets all components in an undefined, but consistent (within a single run) ordering.
	 */
	public List<Component> getComponents() {
		return components;
	}

	/**
	 * Registers a new component.
	 */
	public void addComponent(Component component) {
		components.add(component);
		// Order the components by classname, just to have a consistent output
		// independent of the order components happened to be registered.
		Collections.sort(
			components,
			(o1, o2) -> o1.getClass().getName().compareTo(o2.getClass().getName())
		);
	}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="Themes">
	/**
	 * The default theme is used when no other theme is registered.
	 */
	public static final String DEFAULT_THEME_NAME = "base";

	/**
	 * The themes in order added.
	 */
	private final Map<String,Theme> themes = new LinkedHashMap<>();

	/**
	 * Gets the themes, in the order added.
	 */
	public Map<String,Theme> getThemes() {
		synchronized(themes) {
			// Not returning a copy since themes are normally only registered on app start-up.
			return Collections.unmodifiableMap(themes);
		}
	}

	/**
	 * Registers a new theme.
	 *
	 * @throws  IllegalStateException  if a theme is already registered with the name.
	 */
	public void addTheme(Theme theme) throws IllegalStateException {
		String name = theme.getName();
		synchronized(themes) {
			if(themes.containsKey(name)) throw new IllegalStateException("Theme already registered: " + name);
			if(themes.put(name, theme) != null) throw new AssertionError();
		}
	}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="CSS Links">
	/**
	 * The CSS links in the order added.
	 */
	private final Set<String> cssLinks = new LinkedHashSet<>();

	/**
	 * Gets the CSS links, in the order added.
	 */
	public Set<String> getCssLinks() {
		synchronized(cssLinks) {
			// Not returning a copy since CSS links are normally only registered on app start-up.
			return Collections.unmodifiableSet(cssLinks);
		}
	}

	/**
	 * Registers a new CSS link.
	 *
	 * @throws  IllegalStateException  if the link is already registered.
	 */
	public void addCssLink(String cssLink) throws IllegalStateException {
		synchronized(cssLinks) {
			if(!cssLinks.add(cssLink)) throw new IllegalStateException("CSS link already registered: " + cssLink);
		}
	}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="Print CSS Links">
	/**
	 * The print CSS links in the order added.
	 */
	private final Set<String> printCssLinks = new LinkedHashSet<>();

	/**
	 * Gets the print CSS links, in the order added.
	 */
	public Set<String> getPrintCssLinks() {
		synchronized(printCssLinks) {
			// Not returning a copy since CSS links are normally only registered on app start-up.
			return Collections.unmodifiableSet(printCssLinks);
		}
	}

	/**
	 * Registers a new print CSS link.
	 *
	 * @throws  IllegalStateException  if the link is already registered.
	 */
	public void addPrintCssLink(String printCssLink) throws IllegalStateException {
		synchronized(printCssLinks) {
			if(!printCssLinks.add(printCssLink)) throw new IllegalStateException("Print CSS link already registered: " + printCssLink);
		}
	}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="Scripts">
	/**
	 * The scripts in the order added.
	 */
	private final Map<String,String> scripts = new LinkedHashMap<>();

	/**
	 * Gets the scripts, in the order added.
	 */
	public Map<String,String> getScripts() {
		synchronized(scripts) {
			// Not returning a copy since scripts are normally only registered on app start-up.
			return Collections.unmodifiableMap(scripts);
		}
	}

	/**
	 * Registers a new script.  When a script is added multiple times,
	 * the src must be consistent between adds.  Also, a src may not be
	 * added under different names.
	 *
	 * @param  name  the name of the script, independent of version and src
	 * @param  src   the src of the script.
	 *
	 * @throws  IllegalStateException  if the script already registered but with a different src.
	 */
	public void addScript(String name, String src) throws IllegalStateException {
		synchronized(scripts) {
			String existingSrc = scripts.get(name);
			if(existingSrc != null) {
				if(!src.equals(existingSrc)) {
					throw new IllegalStateException(
						"Script already registered but with a different src:"
						+ " name=" + name
						+ " src=" + src
						+ " existingSrc=" + existingSrc
					);
				}
			} else {
				// Make sure src not provided by another script
				if(scripts.values().contains(src)) {
					throw new IllegalArgumentException("Non-unique global script src: " + src);
				}
				if(scripts.put(name, src) != null) throw new AssertionError();
			}
		}
	}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="Head Includes">
	/**
	 * The head includes in the order added.
	 */
	private final Set<String> headIncludes = new LinkedHashSet<>();

	/**
	 * Gets the head includes, in the order added.
	 */
	public Set<String> getHeadIncludes() {
		synchronized(headIncludes) {
			// Not returning a copy since head includes are normally only registered on app start-up.
			return Collections.unmodifiableSet(headIncludes);
		}
	}

	/**
	 * Registers a new head include.
	 *
	 * @throws  IllegalStateException  if the link is already registered.
	 */
	public void addHeadInclude(String headInclude) throws IllegalStateException {
		synchronized(headIncludes) {
			if(!headIncludes.add(headInclude)) throw new IllegalStateException("headInclude already registered: " + headInclude);
		}
	}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="Links to Elements">

	/**
	 * Resolves the link CSS class for the given types of elements.
	 */
	@FunctionalInterface
	public static interface LinkCssClassResolver<E extends com.semanticcms.core.model.Element> {
		/**
		 * Gets the CSS class to use in links to the given element.
		 * When null is returned, any resolvers for super classes will also be invoked.
		 *
		 * @return  The CSS class name or {@code null} when none configured for the provided element.
		 */
		String getCssLinkClass(E element);
	}

	/**
	 * The CSS classes used in links.
	 */
	private final Map<Class<? extends com.semanticcms.core.model.Element>,LinkCssClassResolver<?>> linkCssClassResolverByElementType = new LinkedHashMap<>();

	/**
	 * Gets the CSS class to use in links to the given element.
	 * Also looks for match on parent classes up to and including Element itself.
	 * 
	 * @return  The CSS class or {@code null} when element is null or no class registered for it or any super class.
	 *
	 * @see  LinkCssClassResolver#getCssLinkClass(com.semanticcms.core.model.Element)
	 */
	public <E extends com.semanticcms.core.model.Element> String getLinkCssClass(E element) {
		if(element == null) return null;
		Class<? extends com.semanticcms.core.model.Element> elementType = element.getClass();
		synchronized(linkCssClassResolverByElementType) {
			while(true) {
				@SuppressWarnings("unchecked")
				LinkCssClassResolver<? super E> linkCssClassResolver = (LinkCssClassResolver<? super E>)linkCssClassResolverByElementType.get(elementType);
				if(linkCssClassResolver != null) {
					String linkCssClass = linkCssClassResolver.getCssLinkClass(element);
					if(linkCssClass != null) return linkCssClass;
				}
				if(elementType == com.semanticcms.core.model.Element.class) return null;
				elementType = elementType.getSuperclass().asSubclass(com.semanticcms.core.model.Element.class);
			}
		}
	}

	/**
	 * Registers a new CSS resolver to use in link to the given type of element.
	 *
	 * @throws  IllegalStateException  if the element type is already registered.
	 */
	public <E extends com.semanticcms.core.model.Element> void addLinkCssClassResolver(
		Class<E> elementType,
		LinkCssClassResolver<? super E> cssLinkClassResolver
	) throws IllegalStateException {
		synchronized(linkCssClassResolverByElementType) {
			if(linkCssClassResolverByElementType.containsKey(elementType)) throw new IllegalStateException("Link CSS class already registered: " + elementType);
			if(linkCssClassResolverByElementType.put(elementType, cssLinkClassResolver) != null) throw new AssertionError();
		}
	}

	/**
	 * Registers a new CSS class to use in link to the given type of element.
	 *
	 * @throws  IllegalStateException  if the element type is already registered.
	 */
	public <E extends com.semanticcms.core.model.Element> void addLinkCssClass(
		Class<E> elementType,
		String cssLinkClass
	) throws IllegalStateException {
		addLinkCssClassResolver(elementType, element -> cssLinkClass);
	}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="Lists of Nodes">

	/**
	 * Resolves the list item CSS class for the given types of nodes.
	 */
	@FunctionalInterface
	public static interface ListItemCssClassResolver<N extends com.semanticcms.core.model.Node> {
		/**
		 * Gets the CSS class to use in list items to the given node.
		 * When null is returned, any resolvers for super classes will also be invoked.
		 *
		 * @return  The CSS class name or {@code null} when none configured for the provided node.
		 */
		String getListItemCssClass(N node);
	}

	/**
	 * The CSS classes used in list items.
	 */
	private final Map<Class<? extends com.semanticcms.core.model.Node>,ListItemCssClassResolver<?>> listItemCssClassResolverByNodeType = new LinkedHashMap<>();

	/**
	 * Gets the CSS class to use in list items to the given node.
	 * Also looks for match on parent classes up to and including Node itself.
	 *
	 * @return  The CSS class or {@code null} when node is null or no class registered for it or any super class.
	 *
	 * @see  ListItemCssClassResolver#getListItemCssClass(com.semanticcms.core.model.Node)
	 */
	public <N extends com.semanticcms.core.model.Node> String getListItemCssClass(N node) {
		if(node == null) return null;
		Class<? extends com.semanticcms.core.model.Node> nodeType = node.getClass();
		synchronized(listItemCssClassResolverByNodeType) {
			while(true) {
				@SuppressWarnings("unchecked")
				ListItemCssClassResolver<? super N> listItemCssClassResolver = (ListItemCssClassResolver<? super N>)listItemCssClassResolverByNodeType.get(nodeType);
				if(listItemCssClassResolver != null) {
					String listItemCssClass = listItemCssClassResolver.getListItemCssClass(node);
					if(listItemCssClass != null) return listItemCssClass;
				}
				if(nodeType == com.semanticcms.core.model.Node.class) return null;
				nodeType = nodeType.getSuperclass().asSubclass(com.semanticcms.core.model.Node.class);
			}
		}
	}

	/**
	 * Registers a new CSS resolver to use in list items to the given type of node.
	 *
	 * @throws  IllegalStateException  if the node type is already registered.
	 */
	public <N extends com.semanticcms.core.model.Node> void addListItemCssClassResolver(
		Class<N> nodeType,
		ListItemCssClassResolver<? super N> listItemCssClassResolver
	) throws IllegalStateException {
		synchronized(listItemCssClassResolverByNodeType) {
			if(listItemCssClassResolverByNodeType.containsKey(nodeType)) throw new IllegalStateException("List item CSS class already registered: " + nodeType);
			if(listItemCssClassResolverByNodeType.put(nodeType, listItemCssClassResolver) != null) throw new AssertionError();
		}
	}

	/**
	 * Registers a new CSS class to use in list items to the given type of node.
	 *
	 * @throws  IllegalStateException  if the node type is already registered.
	 */
	public <N extends com.semanticcms.core.model.Node> void addListItemCssClass(
		Class<N> nodeType,
		String listItemCssClass
	) throws IllegalStateException {
		addListItemCssClassResolver(nodeType, node -> listItemCssClass);
	}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="Concurrency">

	/**
	 * Initialization parameter, that when set to "true" will enable the
	 * concurrent subrequest processing feature.  This is still experimental
	 * and is off by default.
	 */
	private static final String CONCURRENT_SUBREQUESTS_INIT_PARAM = SemanticCMS.class.getName() + ".concurrentSubrequests";

	private final boolean concurrentSubrequests;

	/**
	 * Checks if concurrent subrequests are allowed.
	 */
	boolean getConcurrentSubrequests() {
		return concurrentSubrequests;
	}

	private final Executors executors;

	/**
	 * A shared executor available to all components.
	 * <p>
	 * Consider selecting concurrent or sequential implementations based on overall system load.
	 * See {@link ConcurrencyCoordinator#isConcurrentProcessingRecommended(javax.servlet.ServletRequest)}.
	 * </p>
	 * @see  ConcurrencyCoordinator#isConcurrentProcessingRecommended(javax.servlet.ServletRequest)
	 */
	public Executors getExecutors() {
		return executors;
	}
	// </editor-fold>
}
