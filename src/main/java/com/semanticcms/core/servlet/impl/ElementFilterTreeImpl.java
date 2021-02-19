/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2016, 2019, 2020, 2021  AO Industries, Inc.
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
package com.semanticcms.core.servlet.impl;

import static com.aoindustries.encoding.TextInXhtmlAttributeEncoder.encodeTextInXhtmlAttribute;
import com.aoindustries.html.Document;
import com.aoindustries.net.URIEncoder;
import com.semanticcms.core.model.ChildRef;
import com.semanticcms.core.model.Element;
import com.semanticcms.core.model.Node;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.servlet.CaptureLevel;
import com.semanticcms.core.servlet.CapturePage;
import com.semanticcms.core.servlet.CurrentNode;
import com.semanticcms.core.servlet.PageIndex;
import com.semanticcms.core.servlet.SemanticCMS;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.SkipPageException;

/**
 * Builds a tree, filtering for a specific element type.
 */
final public class ElementFilterTreeImpl {

	/**
	 * A filter to select elements by arbitrary conditions.
	 */
	@FunctionalInterface
	public static interface ElementFilter {

		/**
		 * Checks if matches.
		 */
		boolean matches(Element e);
	}

	/**
	 * A filter to select non-hidden and by element class.
	 */
	public static class ClassFilter implements ElementFilter {

		private final Class<? extends Element> elementType;

		public ClassFilter(Class<? extends Element> elementType) {
			this.elementType = elementType;
		}

		@Override
		public boolean matches(Element e) {
			return !e.isHidden() && elementType.isInstance(e);			
		}
	}

	private static boolean findElements(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		ElementFilter elementFilter,
		Set<Node> nodesWithMatches,
		Node node,
		boolean includeElements
	) throws ServletException, IOException {
		List<Element> childElements = node.getChildElements();
		boolean hasMatch;
		// Add self if is the target type
		if((node instanceof Element) && elementFilter.matches((Element)node)) {
			hasMatch = true;
		} else {
			hasMatch = false;
			for(Element childElem : childElements) {
				if(elementFilter.matches(childElem)) {
					hasMatch = true;
					break;
				}
			}
		}
		if(includeElements) {
			for(Element childElem : childElements) {
				if(findElements(servletContext, request, response, elementFilter, nodesWithMatches, childElem, includeElements)) {
					hasMatch = true;
				}
			}
		} else {
			assert (node instanceof Page);
			if(!hasMatch) {
				// Not including elements, so any match from an element must be considered a match from the page the element is on
				Page page = (Page)node;
				for(Element e : page.getElements()) {
					if(elementFilter.matches(e)) {
						hasMatch = true;
						break;
					}
				}
			}
		}
		if(node instanceof Page) {
			for(ChildRef childRef : ((Page)node).getChildRefs()) {
				PageRef childPageRef = childRef.getPageRef();
				// Child not in missing book
				if(childPageRef.getBook() != null) {
					Page child = CapturePage.capturePage(servletContext, request, response, childPageRef, CaptureLevel.META);
					if(findElements(servletContext, request, response, elementFilter, nodesWithMatches, child, includeElements)) {
						hasMatch = true;
					}
				}
			}
		}
		if(hasMatch) {
			nodesWithMatches.add(node);
		}
		return hasMatch;
	}

	private static void writeNode(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Node currentNode,
		Set<Node> nodesWithMatches,
		PageIndex pageIndex,
		Document document,
		Node node,
		boolean includeElements
	) throws ServletException, IOException, SkipPageException {
		final Page page;
		final Element element;
		if(node instanceof Page) {
			page = (Page)node;
			element = null;
		} else if(node instanceof Element) {
			assert includeElements;
			element = (Element)node;
			page = element.getPage();
		} else {
			throw new AssertionError();
		}
		final PageRef pageRef = page.getPageRef();
		if(currentNode != null) {
			// Add page links
			currentNode.addPageLink(pageRef);
		}
		if(document != null) {
			document.out.write("<li");
			SemanticCMS semanticCMS = SemanticCMS.getInstance(servletContext);
			String listItemCssClass = semanticCMS.getListItemCssClass(node);
			if(listItemCssClass != null) {
				document.out.write(" class=\"");
				encodeTextInXhtmlAttribute(listItemCssClass, document.out);
				document.out.write('"');
			}
			document.out.write("><a href=\"");
			StringBuilder url = new StringBuilder();
			Integer index = pageIndex==null ? null : pageIndex.getPageIndex(pageRef);
			if(index != null) {
				url.append('#');
				URIEncoder.encodeURIComponent(
					PageIndex.getRefId(
						index,
						element==null ? null : element.getId()
					),
					url
				);
			} else {
				URIEncoder.encodeURI(request.getContextPath(), url);
				URIEncoder.encodeURI(pageRef.getServletPath(), url);
				if(element != null) {
					String elemId = element.getId();
					assert elemId != null;
					url.append('#');
					URIEncoder.encodeURIComponent(elemId, url);
				}
			}
			encodeTextInXhtmlAttribute(
				response.encodeURL(
					url.toString()
				),
				document.out
			);
			document.out.write("\">");
			document.text(node.getLabel());
			if(index != null) {
				document.out.write("<sup>[");
				document.text(index + 1);
				document.out.write("]</sup>");
			}
			document.out.write("</a>");
		}
		List<Node> childNodes = NavigationTreeImpl.getChildNodes(servletContext, request, response, includeElements, true, node);
		childNodes = NavigationTreeImpl.filterNodes(childNodes, nodesWithMatches);
		if(!childNodes.isEmpty()) {
			if(document != null) {
				document.out.write("\n<ul>\n");
			}
			for(Node childNode : childNodes) {
				writeNode(servletContext, request, response, currentNode, nodesWithMatches, pageIndex, document, childNode, includeElements);
			}
			if(document != null) {
				document.out.write("</ul>\n");
			}
		}
		if(document != null) {
			document.out.write("</li>\n");
		}
	}

	// Traversal-based implementation is proving too complicated due to needing to
	// look ahead to know which elements to show.
	// TODO: Caching?
	public static void writeElementFilterTreeImpl(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Document document,
		ElementFilter elementFilter,
		Node root,
		boolean includeElements
	) throws ServletException, IOException, SkipPageException {
		// Get the current capture state
		final CaptureLevel captureLevel = CaptureLevel.getCaptureLevel(request);
		if(captureLevel.compareTo(CaptureLevel.META) >= 0) {
			final Node currentNode = CurrentNode.getCurrentNode(request);
			// Filter by has files
			final Set<Node> nodesWithMatches = new HashSet<>();
			findElements(servletContext, request, response, elementFilter, nodesWithMatches, root, includeElements);

			if(captureLevel == CaptureLevel.BODY) {
				document.out.write("<ul>\n");
			}
			writeNode(
				servletContext,
				request,
				response,
				currentNode,
				nodesWithMatches,
				PageIndex.getCurrentPageIndex(request),
				captureLevel == CaptureLevel.BODY ? document : null,
				root,
				includeElements
			);
			if(captureLevel == CaptureLevel.BODY) {
				document.out.write("</ul>\n");
			}
		}
	}

	public static void writeElementFilterTreeImpl(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Document document,
		Class<? extends Element> elementType,
		Node root,
		boolean includeElements
	) throws ServletException, IOException, SkipPageException {
		writeElementFilterTreeImpl(
			servletContext,
			request,
			response,
			document,
			new ClassFilter(elementType),
			root,
			includeElements
		);
	}

	/**
	 * Make no instances.
	 */
	private ElementFilterTreeImpl() {
	}
}
