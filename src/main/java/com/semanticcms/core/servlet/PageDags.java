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
package com.semanticcms.core.servlet;

import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Utilities for working with directed acyclic graphs (DAGs) of pages.
 */
final public class PageDags {

	// TODO: Concurrent: Many / most places that use this can do a more direct depth-first traversal
	public static List<Page> convertPageDagToList(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Page rootPage,
		CaptureLevel level
	) throws ServletException, IOException {
		Set<PageRef> seenPages = new HashSet<PageRef>();
		List<Page> list = new ArrayList<Page>();
		addPage(
			servletContext,
			request,
			response,
			rootPage,
			level,
			seenPages,
			list
		);
		return Collections.unmodifiableList(list);
	}

	private static void addPage(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Page page,
		CaptureLevel level,
		Set<PageRef> seenPages,
		List<Page> list
	) throws ServletException, IOException {
		seenPages.add(page.getPageRef());
		list.add(page);
		for(PageRef childRef : page.getChildPages()) {
			if(
				// Child not in missing book
				childRef.getBook() != null
				// Not already seen
				&& !seenPages.contains(childRef)
			) {
				Page child = CapturePage.capturePage(servletContext, request, response, childRef, level);
				addPage(servletContext, request, response, child, level, seenPages, list);
			}
		}
	}

	/**
	 * Make no instances.
	 */
	private PageDags() {
	}
}
