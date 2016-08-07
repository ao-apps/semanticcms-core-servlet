/*
 * ao-web-page-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2013, 2014, 2016  AO Industries, Inc.
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

/*
 * JavaScript available to any page in the project.
 * After including this script, be sure to set the openFileUrl to a correct value.
 */
ao_web_page_servlet = {

	/*
	 * The openFileUrl of the application.  This is set by JSP just after this
	 * script is included.
	 */
	openFileUrl : "",

	/*
	 * Handles error messages from Ajax calls.
	 */
	handleAjaxError : function(message, jqXHR, errorThrown) {
		window.alert(
			message + "\n"
			+ "Status Code = " + jqXHR.status + "\n"
			+ "Error Thrown = " + errorThrown
		);
	},

	/*
	 * Opens a file from the server, passing-in the full path to the file.
	 * This is to be used in a trusted environment only.
	 */
	openFile : function(book, path) {
		// window.alert("path="+path);
		$.ajax({
			cache : false,
			type : "POST", // POST because has side-effects
			timeout : 60000,
			url : ao_web_page_servlet.openFileUrl,
			data : {
				book : book,
				path : path
			},
			dataType : "xml",
			success : function(data, textStatus, jqXHR) {
				// Do nothing, file is opened by the servlet container
			},
			error : function(jqXHR, textStatus, errorThrown) {
				ao_web_page_servlet.handleAjaxError(
					"Unable to open file:\n"
					+ "book = " + book + "\n"
					+ "path = " + path,
					jqXHR,
					errorThrown
				);
			}
		});
	}
};
