/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2016  AO Industries, Inc.
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

import com.semanticcms.core.model.Author;
import com.semanticcms.core.model.Copyright;
import com.semanticcms.core.model.Page;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.SkipPageException;

/**
 * A site may provide multiple views of the data.  Except the default content view,
 * views will typically show something about the current page and all of its children.
 */
abstract public class View implements Comparable<View> {

	/**
	 * The separator used between segments of the title.
	 * Should this be provided by the template?
	 */
	protected static final String TITLE_SEPARATOR = " - ";

	/**
	 * View groupings, in order.
	 */
	public enum Group {
		/**
		 * Things that should be placed absolutely first.
		 */
		FIRST,

		/**
		 * The first set of views are those that are more fixed - typically displayed on every page.
		 */
		FIXED,

		/**
		 * The second set of views are those that are hidden when not relevant to the current page or any of its children.
		 * This often includes the per-element-type views.
		 */
		VARIABLE
	}

	/**
	 * Orders by group, display, then name.
	 */
	@Override
	public int compareTo(View o) {
		int diff = getGroup().compareTo(o.getGroup());
		if(diff != 0) return diff;
		diff = getDisplay().compareTo(o.getDisplay());
		if(diff != 0) return diff;
		diff = getName().compareTo(o.getName());
		return diff;
	}

	/**
	 * Two views with the same name are considered equal.
	 */
	@Override
	public boolean equals(Object obj) {
		if(!(obj instanceof View)) return false;
		View o = (View)obj;
		return getName().equals(o.getName());
	}

	/**
	 * Consistent with equals, hashCode based on name.
	 */
	@Override
	public int hashCode() {
		return getName().hashCode();
	}

	/**
	 * @see  #getDisplay()
	 */
	@Override
	public String toString() {
		return getDisplay();
	}

	/**
	 * Gets the grouping for this view.
	 */
	abstract public Group getGroup();

	/**
	 * Gets the display name for this view.
	 */
	abstract public String getDisplay();

	/**
	 * Gets the unique name of this view.
	 */
	abstract public String getName();

	/**
	 * Checks if this is the default view.
	 */
	final public boolean isDefault() {
		return SemanticCMS.DEFAULT_VIEW_NAME.equals(getName());
	}

	/**
	 * Checks if a view applies in global navigation context.
	 * 
	 * @implSpec  returns {@code true} by default
	 */
	public boolean getAppliesGlobally() {
		return true;
	}

	/**
	 * Checks if a view is applicable the given request and page.
	 * 
	 * @implSpec  returns {@code true} by default
	 */
	public boolean isApplicable(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Page page
	) throws ServletException, IOException {
		return true;
	}

	/**
	 * Gets an id to use for the main navigation link to this view.
	 *
	 * @return  the ID or null for none
	 *
	 * @implSpec  returns {@code null} by default
	 */
	public String getLinkId() {
		return null;
	}

	/**
	 * Gets the CSS class to use for the main navigation link to this view.
	 *
	 * @return  the CSS class or null for none
	 *
	 * @implSpec  returns {@code null} by default
	 */
	public String getLinkCssClass(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response
	) {
		return null;
	}

	/**
	 * Gets the optional additional parameter to a view link.
	 *
	 * @implSpec  returns empty map
	 */
	public Map<String,List<String>> getLinkParams(ServletContext servletContext, HttpServletRequest request, HttpServletResponse response, Page page) {
		return Collections.emptyMap();
	}

	/**
	 * Gets the copyright information for the view on the given page.
	 * 
	 * @see  CopyrightUtils#findCopyright(javax.servlet.ServletContext, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, com.semanticcms.core.model.Page)
	 */
	public Copyright getCopyright(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Page page
	) throws ServletException, IOException {
		return CopyrightUtils.findCopyright(servletContext, request, response, page);
	}

	/**
	 * Gets the author(s) for the view on the given page.
	 *
	 * @see  AuthorUtils#findAuthors(javax.servlet.ServletContext, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, com.semanticcms.core.model.Page)
	 */
	public Set<Author> getAuthors(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Page page
	) throws ServletException, IOException {
		return AuthorUtils.findAuthors(servletContext, request, response, page);
	}

	/**
	 * Gets the page title for the view on the given page.
	 *
	 * Defaults to: "view.display - page.title - page.pageRef.book.title"
	 */
	public String getTitle(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Page page
	) {
		return getDisplay() + TITLE_SEPARATOR + page.getTitle() + TITLE_SEPARATOR + page.getPageRef().getBook().getTitle();
	}

	/**
	 * Gets the description for this view of the given page or {@code null} for none.
	 */
	abstract public String getDescription(Page page);

	/**
	 * Gets the keywords for this view of the given page or {@code null} for none.
	 */
	abstract public String getKeywords(Page page);

	/**
	 * Gets an optional set of additional CSS resources to include for this view
	 * in the order they should be added.
	 */
	public Set<String> getCssLinks() {
		return Collections.emptySet();
	}

	/**
	 * Gets any per-view scripts, when have the same name as globally registered
	 * scripts, must have matching src.
	 *
	 * @see  SemanticCMS#getScripts()
	 *
	 * @implSpec  returns empty map by default
	 */
	public Map<String,String> getScripts() {
		return Collections.emptyMap();
	}

	/**
	 * Gets whether robots are allowed to access this view to the given page.  When true will include both
	 * "noindex, nofollow" in the head and put "nofollow" on links to this view.
	 */
	abstract public boolean getAllowRobots(Page page);

	/**
	 * Renders the view.  This is called by the template to fill-out the main content area.
	 */
	abstract public void doView(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Page page
	) throws ServletException, IOException, SkipPageException;
}
