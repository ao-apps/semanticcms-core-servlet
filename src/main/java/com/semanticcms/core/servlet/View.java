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

/**
 * A site may provide multiple views of the data.  Except the default content view,
 * views will typically show something about the current page and all of its children.
 */
abstract public class View implements Comparable<View> {

	/**
	 * View groupings, in order.
	 */
	public enum Group {
		/**
		 * The first set of views are those that are more fixed - typically displayed on every page.
		 */
		FIXED,

		/**
		 * The second set of views are those that are hidden when not relevant to the current page or any of its children.
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
	abstract Group getGroup();

	/**
	 * Gets the display name for this view.
	 */
	abstract String getDisplay();

	/**
	 * Gets the unique name of this view.
	 */
	abstract String getName();
}
