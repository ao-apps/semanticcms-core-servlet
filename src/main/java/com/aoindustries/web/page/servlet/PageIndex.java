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
package com.aoindustries.web.page.servlet;

import com.aoindustries.lang.NullArgumentException;
import com.aoindustries.web.page.Page;
import com.aoindustries.web.page.PageRef;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Captures all pages recursively and builds an index of pages
 * for fast page number lookups.
 * The index may be created on the entire site or single subtree.
 * The index is used when presenting multiple pages in a combined view.
 */
public class PageIndex {

	/**
	 * The request scope variable containing any active page index.
	 */
	public static final String PAGE_INDEX_REQUEST_ATTRIBUTE_NAME = "pageIndex";

	/**
	 * Gets the current page index setup by a combined view or <code>null</code>
	 * if not doing a combined view.
	 */
	public static PageIndex getCurrentPageIndex(ServletRequest request) {
		NullArgumentException.checkNotNull(request, "request");
		return (PageIndex)request.getAttribute(PAGE_INDEX_REQUEST_ATTRIBUTE_NAME);
	}

	/**
	 * Captures the root with META capture level and all children as PAGE.
	 */
	public static PageIndex getPageIndex(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		PageRef rootPageRef
	) throws ServletException, IOException {
		return new PageIndex(
			servletContext,
			request,
			response,
			CapturePage.capturePage(
				servletContext,
				request,
				response,
				rootPageRef,
				CaptureLevel.META
			)
		);
	}

	/**
	 * Gets an id for use in referencing the page at the given index.
	 * If the index is non-null, as in a combined view, will be "page#-id".
	 * Otherwise, the id is unchanged.
	 *
	 * @param  id  optional, id not added when null or empty
	 * 
	 * @see  #appendIdInPage(java.lang.Integer, java.lang.String, java.lang.Appendable) 
	 */
	public static String getRefId(Integer index, String id) throws IOException {
		if(index != null) {
			String indexPlusOne = Integer.toString(index + 1);
			StringBuilder out = new StringBuilder(
				4 // "page"
				+ indexPlusOne.length()
				+ (
					id==null || id.isEmpty()
					? 0
					: (
						1 // '-'
						+ id.length()
					)
				)
			);
			out.append("page");
			out.append(indexPlusOne);
			if(id != null && !id.isEmpty()) {
				out.append('-');
				out.append(id);
			}
			return out.toString();
		} else {
			return id;
		}
	}

	/**
	 * Gets an id for use in the current page.  If the page is part of a page index,
	 * as in a combined view, will be "page#-id".  Otherwise, the id is unchanged.
	 *
	 * @param  id  optional, id not added when null or empty
	 */
	public static String getRefId(
		ServletContext servletContext,
		HttpServletRequest request,
		String id
	) throws ServletException {
		PageIndex pageIndex = getCurrentPageIndex(request);
		// No page index
		if(pageIndex == null) return id;
		Integer index = pageIndex.getPageIndex(PageRefResolver.getCurrentPageRef(servletContext, request));
		// Page not in index
		if(index == null) return id;
		if(id == null || id.isEmpty()) {
			// Page in index
			return "page" + (index + 1);
		} else {
			// Page in index with id
			return "page" + (index + 1) + '-' + id;
		}
	}

	/**
	 * Gets an id for use in referencing the given page.  If the page is part of a page index,
	 * as in a combined view, will be "page#-id".  Otherwise, the id is unchanged.
	 *
	 * @param  id  optional, id not added when null or empty
	 *
	 * @see  #appendIdInPage(com.aoindustries.web.page.servlet.PageIndex, com.aoindustries.web.page.Page, java.lang.String, java.lang.Appendable)
	 */
	public static String getRefIdInPage(
		ServletContext servletContext,
		HttpServletRequest request,
		Page page,
		String id
	) {
		PageIndex pageIndex = getCurrentPageIndex(request);
		// No page index
		if(pageIndex == null) return id;
		Integer index = pageIndex.getPageIndex(page.getPageRef());
		// Page not in index
		if(index == null) return id;
		if(id == null || id.isEmpty()) {
			// Page in index
			return "page" + (index + 1);
		} else {
			// Page in index with id
			return "page" + (index + 1) + '-' + id;
		}
	}

	/**
	 * Appends an id for use in referencing the page at the given index.
	 * If the index is non-null, as in a combined view, will be "page#-id".
	 * Otherwise, the id is unchanged.
	 *
	 * @param  id  optional, id not added when null or empty
	 */
	public static void appendIdInPage(Integer index, String id, Appendable out) throws IOException {
		if(index != null) {
			out.append("page");
			out.append(Integer.toString(index + 1));
			if(id != null && !id.isEmpty()) out.append('-');
		}
		if(id != null && !id.isEmpty()) out.append(id);
	}

	/**
	 * Appends an id for use in referencing the given page.
	 *
	 * @param  id  optional, id not added when null or empty
	 *
	 * @see  #getRefIdInPage(javax.servlet.ServletContext, javax.servlet.http.HttpServletRequest, com.aoindustries.web.page.Page, java.lang.String)
	 */
	public static void appendIdInPage(PageIndex pageIndex, Page page, String id, Appendable out) throws IOException {
		if(pageIndex != null && page != null) {
			appendIdInPage(
				pageIndex.getPageIndex(page.getPageRef()),
				id,
				out
			);
		} else {
			if(id != null && !id.isEmpty()) out.append(id);
		}
	}

	private final Page rootPage;
	private final List<Page> pageList;
	private final Map<PageRef,Integer> pageIndexes;

	private PageIndex(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Page rootPage
	) throws ServletException, IOException {
		this.rootPage = rootPage;
		this.pageList = PageDags.convertPageDagToList(
			servletContext,
			request,
			response,
			rootPage,
			CaptureLevel.PAGE
		);
		int size = pageList.size();
		// Index pages
		Map<PageRef,Integer> newPageIndexes = new HashMap<PageRef,Integer>(size*4/3+1);
		for(int i=0; i<size; i++) {
			newPageIndexes.put(pageList.get(i).getPageRef(), i);
		}
		this.pageIndexes = Collections.unmodifiableMap(newPageIndexes);
	}

	/**
	 * The root page, captured in META level.
	 */
	public Page getRootPage() {
		return rootPage;
	}

	public List<Page> getPageList() {
		return pageList;
	}

	public Map<PageRef,Integer> getPageIndexes() {
		return pageIndexes;
	}
	
	public Integer getPageIndex(PageRef pagePath) {
		return pageIndexes.get(pagePath);
	}
}