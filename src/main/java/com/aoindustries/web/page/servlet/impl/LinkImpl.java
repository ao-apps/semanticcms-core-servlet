/*
 * ao-web-page-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of ao-web-page-servlet.
 *
 * ao-web-page-servlet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ao-web-page-servlet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with ao-web-page-servlet.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoindustries.web.page.servlet.impl;

import com.aoindustries.encoding.MediaWriter;
import static com.aoindustries.encoding.TextInXhtmlAttributeEncoder.encodeTextInXhtmlAttribute;
import static com.aoindustries.encoding.TextInXhtmlEncoder.encodeTextInXhtml;
import static com.aoindustries.encoding.TextInXhtmlEncoder.textInXhtmlEncoder;
import com.aoindustries.net.HttpParameters;
import com.aoindustries.servlet.http.LastModifiedServlet;
import com.aoindustries.web.page.Element;
import com.aoindustries.web.page.Node;
import com.aoindustries.web.page.Page;
import com.aoindustries.web.page.PageRef;
import com.aoindustries.web.page.servlet.CaptureLevel;
import com.aoindustries.web.page.servlet.CapturePage;
import com.aoindustries.web.page.servlet.CurrentNode;
import com.aoindustries.web.page.servlet.CurrentPage;
import com.aoindustries.web.page.servlet.PageIndex;
import com.aoindustries.web.page.servlet.PageRefResolver;
import java.io.IOException;
import java.io.Writer;
import java.net.URLEncoder;
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
		HttpServletRequest request,
		HttpServletResponse response,
		Writer out,
		String book,
		String page,
		String element,
		String view,
	    HttpParameters params,
		String clazz,
		LinkImplBody<E> body
	) throws E, ServletException, IOException, SkipPageException {
		if(page==null && element==null && view==null) throw new ServletException("If neither element nor view provided, then page is required.");

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
				} else {
					targetElement = null;
				}

				// Write a link to the page

				PageIndex pageIndex = PageIndex.getCurrentPageIndex(request);
				Integer index = pageIndex==null ? null : pageIndex.getPageIndex(targetPageRef);

				out.write("<a");
				String href;
				{
					if(element == null) {
						// Link to page
						if(index != null && view == null) {
							href = '#' + PageIndex.getRefId(index, null);
						} else {
							StringBuilder url = new StringBuilder();
							targetPageRef.appendServletPath(url);
							if(view != null) {
								boolean hasQuestion = url.lastIndexOf("?") != -1;
								url
									.append(hasQuestion ? "&view=" : "?view=")
									.append(URLEncoder.encode(view, responseEncoding));
							}
							href = url.toString();
						}
					} else {
						if(index != null && view == null) {
							// Link to target in indexed page (view=all mode)
							href = '#' + PageIndex.getRefId(index, element);
						} else if(currentPage!=null && currentPage.equals(targetPage) && view == null) {
							// Link to target on same page
							href = '#' + element;
						} else {
							// Link to target on different page (or same page, different view)
							StringBuilder url = new StringBuilder();
							targetPageRef.appendServletPath(url);
							if(view != null) {
								boolean hasQuestion = url.lastIndexOf("?") != -1;
								url
									.append(hasQuestion ? "&view=" : "?view=")
									.append(URLEncoder.encode(view, responseEncoding));
							}
							url.append('#').append(element);
							href = url.toString();
						}
					}
				}
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
				if(clazz != null) {
					if(!clazz.isEmpty()) {
						out.write(" class=\"");
						encodeTextInXhtmlAttribute(clazz, out);
						out.write("\"");
					}
				} else {
					if(targetElement != null) {
						String linkCssClass = targetElement.getLinkCssClass();
						if(linkCssClass != null) {
							out.write(" class=\"");
							encodeTextInXhtmlAttribute(linkCssClass, out);
							out.write('"');
						}
					}
				}
				// No search index all view to avoid duplicate content penalties
				// This nofollow should be added by link creator since view names are not global.
				//if("all".equals(view)) {
				//	out.write(" rel=\"nofollow\"");
				//}
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
				out.write("</a>");
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
