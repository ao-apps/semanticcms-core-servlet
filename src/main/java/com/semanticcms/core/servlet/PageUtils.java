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

import com.semanticcms.core.model.Element;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Utilities for working with pages.
 */
final public class PageUtils {

	public static boolean hasChild(Page page) {
		for(PageRef childRef : page.getChildPages()) {
			if(childRef.getBook() != null) return true;
		}
		return false;
	}

	public static boolean hasElement(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Page page,
		Class<? extends Element> elementType,
		boolean recursive
	) throws ServletException, IOException {
		return hasElementRecursive(
			servletContext,
			request,
			response,
			page,
			elementType,
			recursive,
			recursive ? new HashSet<PageRef>() : null
		);
	}

	private static boolean hasElementRecursive(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Page page,
		Class<? extends Element> elementType,
		boolean recursive,
		Set<PageRef> seenPages
	) throws ServletException, IOException {
		for(Element element : page.getElements()) {
			if(elementType.isAssignableFrom(element.getClass())) {
				return true;
			}
		}
		if(recursive) {
			seenPages.add(page.getPageRef());
			for(PageRef childRef : page.getChildPages()) {
				if(
					// Child not in missing book
					childRef.getBook() != null
					// Not already seen
					&& !seenPages.contains(childRef)
				) {
					if(
						hasElementRecursive(
							servletContext,
							request,
							response,
							CapturePage.capturePage(servletContext, request, response, childRef, CaptureLevel.META),
							elementType,
							recursive,
							seenPages
						)
					) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Make no instances.
	 */
	private PageUtils() {
	}
}
