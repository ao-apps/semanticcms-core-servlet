/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016  AO Industries, Inc.
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

import com.aoindustries.encoding.MediaWriter;
import static com.aoindustries.encoding.TextInXhtmlAttributeEncoder.encodeTextInXhtmlAttribute;
import static com.aoindustries.encoding.TextInXhtmlAttributeEncoder.textInXhtmlAttributeEncoder;
import static com.aoindustries.encoding.TextInXhtmlEncoder.encodeTextInXhtml;
import static com.aoindustries.encoding.TextInXhtmlEncoder.textInXhtmlEncoder;
import com.aoindustries.nio.charset.Charsets;
import com.aoindustries.util.StringUtility;
import com.semanticcms.core.model.Element;
import com.semanticcms.core.model.Node;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.servlet.CaptureLevel;
import com.semanticcms.core.servlet.CapturePage;
import com.semanticcms.core.servlet.CurrentNode;
import com.semanticcms.core.servlet.PageIndex;
import com.semanticcms.core.servlet.PageRefResolver;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

final public class NavigationTreeImpl {

	public static <T> List<T> filterChildren(Collection<T> children, Set<T> pagesToInclude) {
		int size = children.size();
		if(size == 0) return Collections.emptyList();
		List<T> filtered = new ArrayList<T>(size);
		for(T child : children) {
			if(pagesToInclude.contains(child)) {
				filtered.add(child);
			}
		}
		return filtered;
	}

	public static List<Node> getChildNodes(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		boolean includeElements,
		boolean metaCapture,
		Node node
	) throws ServletException, IOException {
		// Both elements and pages are child nodes
		List<Element> childElements = includeElements ? node.getChildElements() : null;
		Set<PageRef> childPages = (node instanceof Page) ? ((Page)node).getChildPages() : null;
		List<Node> childNodes = new ArrayList<Node>(
			(childElements==null ? 0 : childElements.size())
			+ (childPages==null ? 0 : childPages.size())
		);
		if(includeElements) {
			childNodes.addAll(childElements);
		}
		if(childPages != null) {
			for(PageRef childRef : childPages) {
				// Child not in missing book
				if(childRef.getBook() != null) {
					Page childPage = CapturePage.capturePage(servletContext, request, response, childRef, includeElements || metaCapture ? CaptureLevel.META : CaptureLevel.PAGE);
					childNodes.add(childPage);
				}
			}
		}
		return childNodes;
	}

	private static boolean findLinks(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		PageRef linksTo,
		Set<Node> nodesWithLinks,
		Set<Node> nodesWithChildLinks,
		Node node,
		boolean includeElements
	) throws ServletException, IOException {
		boolean hasChildLink = false;
		if(node.getPageLinks().contains(linksTo)) {
			nodesWithLinks.add(node);
			hasChildLink = true;
		}
		if(includeElements) {
			for(Element childElem : node.getChildElements()) {
				if(findLinks(servletContext, request, response, linksTo, nodesWithLinks, nodesWithChildLinks, childElem, includeElements)) {
					hasChildLink = true;
				}
			}
		} else {
			// Not including elements, so any link from an element must be considered a link from the page the element is on
			assert (node instanceof Page);
			Page page = (Page)node;
			for(Element e : page.getElements()) {
				if(e.getPageLinks().contains(linksTo)) {
					nodesWithLinks.add(node);
					hasChildLink = true;
					break;
				}
			}
		}
		if(node instanceof Page) {
			for(PageRef childRef : ((Page)node).getChildPages()) {
				// Child not in missing book
				if(childRef.getBook() != null) {
					Page child = CapturePage.capturePage(servletContext, request, response, childRef, CaptureLevel.META);
					if(findLinks(servletContext, request, response, linksTo, nodesWithLinks, nodesWithChildLinks, child, includeElements)) {
						hasChildLink = true;
					}
				}
			}
		}
		if(hasChildLink) {
			nodesWithChildLinks.add(node);
		}
		return hasChildLink;
	}

	public static String encodeHexData(String data) {
		// Note: This is always UTF-8 encoded and does not depend on response encoding
		return StringUtility.convertToHex(data.getBytes(Charsets.UTF_8));
	}

	private static boolean writeNode(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Writer out,
		Node currentNode,
		Set<Node> nodesWithLinks,
		Set<Node> nodesWithChildLinks,
		PageIndex pageIndex,
		Node node,
		boolean yuiConfig,
		boolean includeElements,
		String target,
		PageRef thisPageRef,
		boolean foundThisPage,
		int maxDepth,
		int level
	) throws IOException, ServletException {
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
		final String servletPath;
		if(out == null) {
			// Will be unused
			servletPath = null;
		} else {
			if(element == null) {
				servletPath = pageRef.getServletPath();
			} else {
				String elemId = element.getId();
				assert elemId != null;
				servletPath = pageRef.getServletPath() + '#' + elemId;
			}
		}
		if(out != null) {
			out.write("<li");
			if(yuiConfig) {
				out.write(" yuiConfig='{\"data\":\"");
				encodeTextInXhtmlAttribute(encodeHexData(servletPath), out);
				out.write("\"}'");
			}
			String listItemCssClass = node.getListItemCssClass();
			if(listItemCssClass == null) listItemCssClass = "ao-web-page-list-item-none";
			out.write(" class=\"");
			encodeTextInXhtmlAttribute(listItemCssClass, out);
			if(level==1) out.write(" expanded");
			out.write("\"><a");
		}
		// Look for thisPage match
		boolean thisPageClass = false;
		if(pageRef.equals(thisPageRef) && element == null) {
			if(!foundThisPage) {
				if(out != null) out.write(" id=\"ao-web-page-tree-this-page\"");
				foundThisPage = true;
			}
			thisPageClass = true;
		}
		// Look for linkToPage match
		boolean linksToPageClass = nodesWithLinks!=null && nodesWithLinks.contains(node);
		if(out != null && (thisPageClass || linksToPageClass)) {
			out.write(" class=\"");
			if(thisPageClass && nodesWithLinks!=null && !linksToPageClass) {
				out.write("ao-web-page-no-link-to-this-page");
			} else if(thisPageClass) {
				out.write("ao-web-page-tree-this-page");
			} else if(linksToPageClass) {
				out.write("ao-web-page-links-to-page");
			} else {
				throw new AssertionError();
			}
			out.write('"');
		}
		if(out != null) {
			if(target != null) {
				out.write(" target=\"");
				encodeTextInXhtmlAttribute(target, out);
				out.write('"');
			}
			out.write(" href=\"");
			Integer index = pageIndex==null ? null : pageIndex.getPageIndex(pageRef);
			if(index != null) {
				out.write('#');
				PageIndex.appendIdInPage(
					index,
					element==null ? null : element.getId(),
					new MediaWriter(textInXhtmlAttributeEncoder, out)
				);
			} else {
				encodeTextInXhtmlAttribute(
					response.encodeURL(
						com.aoindustries.net.UrlUtils.encodeUrlPath(
							request.getContextPath() + servletPath,
							response.getCharacterEncoding()
						)
					),
					out
				);
			}
			out.write("\">");
			node.appendLabel(new MediaWriter(textInXhtmlEncoder, out));
			if(index != null) {
				out.write("<sup>[");
				encodeTextInXhtml(Integer.toString(index+1), out);
				out.write("]</sup>");
			}
			out.write("</a>");
		}
		if(maxDepth==0 || level < maxDepth) {
			List<Node> childNodes = NavigationTreeImpl.getChildNodes(servletContext, request, response, includeElements, false, node);
			if(nodesWithChildLinks!=null) {
				childNodes = NavigationTreeImpl.filterChildren(childNodes, nodesWithChildLinks);
			}
			if(!childNodes.isEmpty()) {
				if(out != null) {
					out.write('\n');
					out.write("<ul>\n");
				}
				for(Node childNode : childNodes) {
					foundThisPage = writeNode(
						servletContext,
						request,
						response,
						out,
						currentNode,
						nodesWithLinks,
						nodesWithChildLinks,
						pageIndex,
						childNode,
						yuiConfig,
						includeElements,
						target,
						thisPageRef,
						foundThisPage,
						maxDepth,
						level+1
					);
				}
				if(out != null) out.write("</ul>\n");
			}
		}
		if(out != null) out.write("</li>\n");
		return foundThisPage;
	}

	public static void writeNavigationTreeImpl(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Writer out,
		Page root,
		boolean skipRoot,
		boolean yuiConfig,
		boolean includeElements,
		String target,
		String thisBook,
		String thisPage,
		String linksToBook,
		String linksToPage,
		int maxDepth
	) throws ServletException, IOException {
		// Get the current capture state
		final CaptureLevel captureLevel = CaptureLevel.getCaptureLevel(request);
		if(captureLevel.compareTo(CaptureLevel.META) >= 0) {
			final Node currentNode = CurrentNode.getCurrentNode(request);
			// Filter by link-to
			final Set<Node> nodesWithLinks;
			final Set<Node> nodesWithChildLinks;
			if(linksToPage == null) {
				if(linksToBook != null) throw new ServletException("linksToPage must be provided when linksToBook is provided.");
				nodesWithLinks = null;
				nodesWithChildLinks = null;
			} else {
				// Find all nodes in the navigation tree that link to the linksToPage
				PageRef linksTo = PageRefResolver.getPageRef(servletContext, request, linksToBook, linksToPage);
				nodesWithLinks = new HashSet<Node>();
				nodesWithChildLinks = new HashSet<Node>();
				findLinks(servletContext, request, response, linksTo, nodesWithLinks, nodesWithChildLinks, root, includeElements);
			}

			PageRef thisPageRef;
			if(thisPage == null) {
				if(thisBook != null) throw new ServletException("thisPage must be provided when thisBook is provided.");
				thisPageRef = null;
			} else {
				thisPageRef = PageRefResolver.getPageRef(servletContext, request, thisBook, thisPage);
			}

			boolean foundThisPage = false;
			PageIndex pageIndex = PageIndex.getCurrentPageIndex(request);
			if(skipRoot) {
				List<Node> childNodes = NavigationTreeImpl.getChildNodes(servletContext, request, response, includeElements, false, root);
				if(nodesWithChildLinks != null) {
					childNodes = NavigationTreeImpl.filterChildren(childNodes, nodesWithChildLinks);
				}
				if(!childNodes.isEmpty()) {
					if(captureLevel == CaptureLevel.BODY) out.write("<ul>\n");
					for(Node childNode : childNodes) {
						foundThisPage = writeNode(
							servletContext,
							request,
							response,
							captureLevel == CaptureLevel.BODY ? out : null,
							currentNode,
							nodesWithLinks,
							nodesWithChildLinks,
							pageIndex,
							childNode,
							yuiConfig,
							includeElements,
							target,
							thisPageRef,
							foundThisPage,
							maxDepth,
							1
						);
					}
					if(captureLevel == CaptureLevel.BODY) out.write("</ul>\n");
				}
			} else {
				if(captureLevel == CaptureLevel.BODY) out.write("<ul>\n");
				/*foundThisPage =*/ writeNode(
					servletContext,
					request,
					response,
					captureLevel == CaptureLevel.BODY ? out : null,
					currentNode,
					nodesWithLinks,
					nodesWithChildLinks,
					pageIndex,
					root,
					yuiConfig,
					includeElements,
					target,
					thisPageRef,
					foundThisPage,
					maxDepth,
					1
				);
				if(captureLevel == CaptureLevel.BODY) out.write("</ul>\n");
			}
		}
	}

	/**
	 * Make no instances.
	 */
	private NavigationTreeImpl() {
	}
}
