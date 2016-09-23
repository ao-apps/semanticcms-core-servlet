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
import com.semanticcms.core.servlet.impl.PageImpl;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletException;

/**
 * A page cache, whether shared between requests or used within the scope of a single request.
 *
 * This implementation is not thread safe, all uses must lock on this cache object itself.
 *
 * TODO: Make two versions, thread-safe and not thread safe, choose based on concurrency and runtime settings
 */
class PageCache {

	/**
	 * Enables the page parent-child relationships verification.
	 */
	private static final boolean VERIFY_CACHE_PARENT_CHILD_RELATIONSHIPS = true;

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

	/**
	 * Tracks which parent pages are still not verified.
	 * <ul>
	 *   <li>Key: The parent pageRef.</li>
	 *   <li>Value: The page(s) that claim the pageRef as a parent but are still not verified.</li>
	 * </ul>
	 * TODO: Could be PageRef instead of Page?
	 */
	private final Map<PageRef,Set<Page>> unverifiedParentsByPageRef = VERIFY_CACHE_PARENT_CHILD_RELATIONSHIPS ? new HashMap<PageRef,Set<Page>>() : null;

	/**
	 * Tracks which child pages are still not verified.
	 * <ul>
	 *   <li>Key: The child pageRef.</li>
	 *   <li>Value: The page(s) that claim the pageRef as a child but are still not verified.</li>
	 * </ul>
	 * TODO: Could be PageRef instead of Page?
	 */
	private final Map<PageRef,Set<Page>> unverifiedChildrenByPageRef = VERIFY_CACHE_PARENT_CHILD_RELATIONSHIPS ? new HashMap<PageRef,Set<Page>>() : null;

	PageCache() {
	}

	Page get(Key key) {
		assert Thread.holdsLock(this);
		return pageCache.get(key);
	}

	Page get(PageRef pageRef, CaptureLevel level) {
		assert Thread.holdsLock(this);
		return get(new Key(pageRef, level));
	}

	private static void addToSet(Map<PageRef,Set<Page>> map, PageRef key, Page page) {
		Set<Page> pages = map.get(key);
		if(pages == null) {
			map.put(key, Collections.singleton(page));
		} else if(pages.size() == 1) {
			pages = new HashSet<Page>(pages);
			pages.add(page);
			map.put(key, pages);
		} else {
			pages.add(page);
		}
	}

	// TODO: Allow null value to represent a body-level capture that has been verified?
	void put(Key key, Page page) throws ServletException {
		assert Thread.holdsLock(this);
		if(pageCache.put(key, page) == null) {
			// Was added
			if(VERIFY_CACHE_PARENT_CHILD_RELATIONSHIPS) {
				final PageRef pageRef = page.getPageRef();
				Set<PageRef> parentPages = null; // Set when first needed
				Set<PageRef> childPages = null; // Set when first needed
				// Verify parents that happened to already be cached
				if(!page.getAllowParentMismatch()) {
					parentPages = page.getParentPages();
					for(PageRef parentRef : parentPages) {
						// Can't verify parent reference to missing book
						if(parentRef.getBook() != null) {
							// Check if parent in cache
							Page parentPage = get(parentRef, CaptureLevel.PAGE);
							if(parentPage == null) parentPage = get(parentRef, CaptureLevel.META);
							if(parentPage != null) {
								PageImpl.verifyChildToParent(pageRef, parentRef, parentPage.getChildPages());
							} else {
								addToSet(unverifiedParentsByPageRef, parentRef, page);
							}
						}
					}
				}
				// Verify children that happened to already be cached
				if(!page.getAllowChildMismatch()) {
					childPages = page.getChildPages();
					for(PageRef childRef : childPages) {
						// Can't verify child reference to missing book
						if(childRef.getBook() != null) {
							// Check if child in cache
							Page childPage = get(childRef, CaptureLevel.PAGE);
							if(childPage == null) childPage = get(childRef, CaptureLevel.META);
							if(childPage != null) {
								PageImpl.verifyParentToChild(pageRef, childRef, childPage.getParentPages());
							} else {
								addToSet(unverifiedChildrenByPageRef, childRef, page);
							}
						}
					}
				}
				// Verify any pages that have claimed this page as their parent and are not yet verified
				Set<Page> unverifiedParents = unverifiedParentsByPageRef.remove(pageRef);
				if(unverifiedParents != null) {
					if(childPages == null) childPages = page.getChildPages();
					for(Page unverifiedParent : unverifiedParents) {
						PageImpl.verifyChildToParent(unverifiedParent.getPageRef(), pageRef, childPages);
					}
				}
				// Verify any pages that have claimed this page as their child and are not yet verified
				Set<Page> unverifiedChildren = unverifiedChildrenByPageRef.remove(pageRef);
				if(unverifiedChildren != null) {
					if(parentPages == null) parentPages = page.getParentPages();
					for(Page unverifiedChild : unverifiedChildren) {
						PageImpl.verifyParentToChild(unverifiedChild.getPageRef(), pageRef, parentPages);
					}
				}
			}
		}
	}
}
