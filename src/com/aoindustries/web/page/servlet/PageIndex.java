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
		Map<PageRef,Integer> newPageIndexes = new HashMap<>(size*4/3+1);
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
