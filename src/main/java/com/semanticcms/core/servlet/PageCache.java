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

import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import java.util.HashMap;
import java.util.Map;

/**
 * A page cache, whether shared between requests or used within the scope of a single request.
 *
 * This implementation is not thread safe, all uses must lock on this cache object itself.
 *
 * TODO: Make two versions, thread-safe and not thread safe, choose based on concurrency and runtime settings
 */
class PageCache {

	/**
	 * Caches pages that have been captured within the scope of a single request.
	 *
	 * IDEA: Could also cache over time, since there is currently no concept of a "user" (except whether request is trusted
	 *       127.0.0.1 or not).
	 */
	static class Key {

		final PageRef pageRef;
		final CaptureLevel level;

		Key(
			PageRef pageRef,
			CaptureLevel level
		) {
			this.pageRef = pageRef;
			assert level != CaptureLevel.BODY : "Body captures are not cached";
			this.level = level;
		}

		@Override
		public boolean equals(Object o) {
			if(!(o instanceof Key)) return false;
			Key other = (Key)o;
			return
				level==other.level
				&& pageRef.equals(other.pageRef)
			;
		}

		private int hash;

		@Override
		public int hashCode() {
			int h = hash;
			if(h == 0) {
				h = level.hashCode();
				h = h * 31 + pageRef.hashCode();
				hash = h;
			}
			return h;
		}

		@Override
		public String toString() {
			return '(' + level.toString() + ", " + pageRef.toString() + ')';
		}
	}

	private final Map<Key,Page> pageCache = new HashMap<Key,Page>();

	Page get(Key key) {
		assert Thread.holdsLock(this);
		return pageCache.get(key);
	}

	Page get(PageRef pageRef, CaptureLevel level) {
		assert Thread.holdsLock(this);
		return get(new Key(pageRef, level));
	}

	void put(Key key, Page page) {
		assert Thread.holdsLock(this);
		pageCache.put(key, page);
	}
}
