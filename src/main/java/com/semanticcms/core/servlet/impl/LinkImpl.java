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

import com.aoindustries.encoding.Coercion;
import com.aoindustries.encoding.MediaWriter;
import static com.aoindustries.encoding.TextInXhtmlAttributeEncoder.encodeTextInXhtmlAttribute;
import static com.aoindustries.encoding.TextInXhtmlAttributeEncoder.textInXhtmlAttributeEncoder;
import static com.aoindustries.encoding.TextInXhtmlEncoder.encodeTextInXhtml;
import static com.aoindustries.encoding.TextInXhtmlEncoder.textInXhtmlEncoder;
import com.aoindustries.net.HttpParameters;
import com.aoindustries.servlet.http.LastModifiedServlet;
import com.semanticcms.core.model.Element;
import com.semanticcms.core.model.Node;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.servlet.CaptureLevel;
import com.semanticcms.core.servlet.CapturePage;
import com.semanticcms.core.servlet.CurrentNode;
import com.semanticcms.core.servlet.CurrentPage;
import com.semanticcms.core.servlet.PageIndex;
import com.semanticcms.core.servlet.PageRefResolver;
import com.semanticcms.core.servlet.SemanticCMS;
import com.semanticcms.core.servlet.View;
import java.io.IOException;
import java.io.Writer;
import java.net.URLEncoder;
import javax.el.ELContext;
import javax.el.ValueExpression;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.SkipPageException;

final public class LinkImpl {

	public static interface LinkImplBody<E extends Throwable> {
		void doBody(boolean discard) throws E, IOException, SkipPageException;
	}

	/**
	 * Writes a broken path reference as "¿/book/path{#targetId}?", no encoding.
	 */
	public static void writeBrokenPath(PageRef pageRef, String targetId, Appendable out) throws IOException {
		out.append('¿');
		out.append(pageRef.getBookPrefix());
		out.append(pageRef.getPath());
		if(targetId != null) {
			out.append('#');
			out.append(targetId);
		}
		out.append('?');
	}

	/**
	 * Writes a broken path reference as "¿/book/path?", no encoding.
	 */
	public static void writeBrokenPath(PageRef pageRef, Appendable out) throws IOException {
		writeBrokenPath(pageRef, null, out);
	}

	public static String getBrokenPath(PageRef pageRef, String targetId) {
		int sbLen = 1 // '¿'
			+ pageRef.getBookPrefix().length()
			+ pageRef.getPath().length();
		if(targetId != null) {
			sbLen +=
				1 // '#'
				+ targetId.length();
		}
		sbLen++; // '?'
		StringBuilder sb = new StringBuilder(sbLen);
		try {
			writeBrokenPath(pageRef, targetId, sb);
		} catch(IOException e) {
			AssertionError ae = new AssertionError("Should not happen on StringBuilder");
			ae.initCause(ae);
			throw ae;
		}
		assert sb.length() == sbLen;
		return sb.toString();
	}

	public static String getBrokenPath(PageRef pageRef) throws IOException {
		return getBrokenPath(pageRef, null);
	}

	/**
	 * Writes a broken path reference as "¿/book/path{#targetId}?", encoding for XHTML.
	 */
	public static void writeBrokenPathInXhtml(PageRef pageRef, String targetId, Appendable out) throws IOException {
		out.append('¿');
		encodeTextInXhtml(pageRef.getBookPrefix(), out);
		encodeTextInXhtml(pageRef.getPath(), out);
		if(targetId != null) {
			out.append('#');
			encodeTextInXhtml(targetId, out);
		}
		out.append('?');
	}

	/**
	 * Writes a broken path reference as "¿/book/path?", encoding for XHTML.
	 */
	public static void writeBrokenPathInXhtml(PageRef pageRef, Appendable out) throws IOException {
		writeBrokenPathInXhtml(pageRef, null, out);
	}

	/**
	 * Writes a broken path reference as "¿/book/path?", encoding for XML attribute.
	 */
	public static void writeBrokenPathInXhtmlAttribute(PageRef pageRef, Appendable out) throws IOException {
		out.append('¿');
		encodeTextInXhtmlAttribute(pageRef.getBookPrefix(), out);
		encodeTextInXhtmlAttribute(pageRef.getPath(), out);
		out.append('?');
	}

	public static <E extends Throwable> void writeLinkImpl(
		ServletContext servletContext,
		ELContext elContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Writer out,
		String book,
		String page,
		String element,
		boolean allowGeneratedElement,
		String viewName,
		boolean small,
	    HttpParameters params,
		Object clazz,
		LinkImplBody<E> body
	) throws E, ServletException, IOException, SkipPageException {
		// Find the view
		final SemanticCMS semanticCMS = SemanticCMS.getInstance(servletContext);
		final View view = semanticCMS.getViewsByName().get(viewName);
		if(view == null) throw new ServletException("View not found: " + viewName);
		final boolean isDefaultView = view.isDefault();
		if(page==null && element==null && isDefaultView) throw new ServletException("If neither element nor view provided, then page is required.");

		// Get the current capture state
		final CaptureLevel captureLevel = CaptureLevel.getCaptureLevel(request);
		if(captureLevel.compareTo(CaptureLevel.META) >= 0) {

			final Node currentNode = CurrentNode.getCurrentNode(request);
			final Page currentPage = CurrentPage.getCurrentPage(request);

			// Use current page when page not set
			PageRef targetPageRef;
			if(page == null) {
				if(book != null) throw new ServletException("page must be provided when book is provided.");
				if(currentPage == null) throw new ServletException("link must be nested in page when page attribute not set.");
				targetPageRef = currentPage.getPageRef();
			} else {
				targetPageRef = PageRefResolver.getPageRef(servletContext, request, book, page);
			}
			// Add page links
			if(currentNode != null) currentNode.addPageLink(targetPageRef);
			if(captureLevel == CaptureLevel.BODY) {
				final String responseEncoding = response.getCharacterEncoding();

				// Capture the page
				Page targetPage;
				if(targetPageRef.getBook()==null) {
					targetPage = null;
				} else if(
					// Short-cut for element already added above within current page
					currentPage != null
					&& targetPageRef.equals(currentPage.getPageRef())
					&& (
						element==null
						|| currentPage.getElementsById().containsKey(element)
					)
				) {
					targetPage = currentPage;
				} else {
					// Capture required, even if capturing self
					targetPage = CapturePage.capturePage(
						servletContext,
						request,
						response,
						targetPageRef,
						element==null ? CaptureLevel.PAGE : CaptureLevel.META
					);
				}

				// Find the element
				Element targetElement;
				if(element != null && targetPage != null) {
					targetElement = targetPage.getElementsById().get(element);
					if(targetElement == null) throw new ServletException("Element not found in target page: " + element);
					if(!allowGeneratedElement && targetPage.getGeneratedIds().contains(element)) throw new ServletException("Not allowed to link to a generated element id, set an explicit id on the target element: " + element);
					if(targetElement.isHidden()) throw new ServletException("Not allowed to link to a hidden element: " + element);
				} else {
					targetElement = null;
				}

				// Write a link to the page

				PageIndex pageIndex = PageIndex.getCurrentPageIndex(request);
				Integer index = pageIndex==null ? null : pageIndex.getPageIndex(targetPageRef);

				out.write(small ? "<span" : "<a");
				String href;
				{
					if(element == null) {
						// Link to page
						if(index != null && isDefaultView) {
							href = '#' + PageIndex.getRefId(index, null);
						} else {
							StringBuilder url = new StringBuilder();
							targetPageRef.appendServletPath(url);
							if(!isDefaultView) {
								boolean hasQuestion = url.lastIndexOf("?") != -1;
								url
									.append(hasQuestion ? "&view=" : "?view=")
									.append(URLEncoder.encode(viewName, responseEncoding));
							}
							href = url.toString();
						}
					} else {
						if(index != null && isDefaultView) {
							// Link to target in indexed page (view=all mode)
							href = '#' + PageIndex.getRefId(index, element);
						} else if(currentPage!=null && currentPage.equals(targetPage) && isDefaultView) {
							// Link to target on same page
							href = '#' + element;
						} else {
							// Link to target on different page (or same page, different view)
							StringBuilder url = new StringBuilder();
							targetPageRef.appendServletPath(url);
							if(!isDefaultView) {
								boolean hasQuestion = url.lastIndexOf("?") != -1;
								url
									.append(hasQuestion ? "&view=" : "?view=")
									.append(URLEncoder.encode(viewName, responseEncoding));
							}
							url.append('#').append(element);
							href = url.toString();
						}
					}
				}
				if(!small) {
					UrlUtils.writeHref(
						servletContext,
						request,
						response,
						out,
						href,
						params,
						false,
						LastModifiedServlet.AddLastModifiedWhen.FALSE
					);
				}
				if(clazz instanceof ValueExpression) {
					clazz = ((ValueExpression)clazz).getValue(elContext);
				}
				if(clazz != null) {
					if(!Coercion.isEmpty(clazz)) {
						out.write(" class=\"");
						Coercion.write(clazz, textInXhtmlAttributeEncoder, out);
						out.write("\"");
					}
				} else {
					if(targetElement != null) {
						String linkCssClass = semanticCMS.getLinkCssClass(targetElement);
						if(linkCssClass != null) {
							out.write(" class=\"");
							encodeTextInXhtmlAttribute(linkCssClass, out);
							out.write('"');
						}
					}
				}
				// Add nofollow consistent with view and page settings.
				if(targetPage != null && !view.getAllowRobots(servletContext, request, response, targetPage)) {
					out.write(" rel=\"nofollow\"");
				}
				out.write('>');

				if(body == null) {
					if(targetElement != null) {
						targetElement.appendLabel(new MediaWriter(textInXhtmlEncoder, out));
					} else if(targetPage!=null) {
						encodeTextInXhtml(targetPage.getTitle(), out);
					} else {
						writeBrokenPathInXhtml(targetPageRef, element, out);
					}
					if(index != null) {
						out.write("<sup>[");
						encodeTextInXhtml(Integer.toString(index+1), out);
						out.write("]</sup>");
					}
				} else {
					body.doBody(false);
				}
				if(small) {
					out.write("<sup><a");
					UrlUtils.writeHref(
						servletContext,
						request,
						response,
						out,
						href,
						params,
						false,
						LastModifiedServlet.AddLastModifiedWhen.FALSE
					);
					out.write(">[link]</a></sup></span>");
				} else {
					out.write("</a>");
				}
			} else {
				// Invoke body for any meta data, but discard any output
				if(body != null) body.doBody(true);
			}
		}
	}

	/**
	 * Make no instances.
	 */
	private LinkImpl() {
	}
}
