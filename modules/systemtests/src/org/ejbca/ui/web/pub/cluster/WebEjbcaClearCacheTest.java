/*************************************************************************
 *                                                                       *
 *  EJBCA: The OpenSource Certificate Authority                          *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/

package org.ejbca.ui.web.pub.cluster;

import java.net.HttpURLConnection;
import java.net.URL;

import junit.framework.TestCase;

import org.apache.log4j.Logger;
import org.ejbca.config.WebConfiguration;
import org.ejbca.core.ejb.config.ConfigurationSessionRemote;
import org.ejbca.util.InterfaceCache;

/**
 *
 * @version $Id: WebEjbcaClearCacheTest.java 11286 2011-01-31 21:27:49Z jeklund $
 */
public class WebEjbcaClearCacheTest extends TestCase {
    private static final Logger log = Logger.getLogger(WebEjbcaClearCacheTest.class);

    private ConfigurationSessionRemote configurationSession = InterfaceCache.getConfigurationSession();

    protected String httpPort;
    protected String httpReqPath;
    protected String httpReqPathNoCommand;

    /**
     * Creates a new TestSignSession object.
     *
     * @param name name
     */
    public WebEjbcaClearCacheTest(String name) {
        super(name);
        httpPort = configurationSession.getProperty(WebConfiguration.CONFIG_HTTPSERVERPUBHTTP, "8080");
        httpReqPath = "http://localhost:" + httpPort + "/ejbca/clearcache/?command=clearcaches";
        httpReqPathNoCommand = "http://localhost:" + httpPort + "/ejbca/clearcache/";
    }

    public void setUp() throws Exception {
    }

    public void tearDown() throws Exception {
    }

    public void testEjbcaClearCacheHttp() throws Exception {
        log.trace(">testEjbcaHealthHttp()");

		URL url = new URL(httpReqPath);
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
        final int responseCode = con.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            log.info("ResponseMessage: " + con.getResponseMessage());
            assertEquals("Response code", HttpURLConnection.HTTP_OK, responseCode);
        }

		url = new URL(httpReqPathNoCommand);
		con = (HttpURLConnection) url.openConnection();
		// SC_BAD_REQUEST returned if we do not gove the command=clearcaches parameter in the request
        final int responseCodeBad = con.getResponseCode();
        if (responseCodeBad != HttpURLConnection.HTTP_BAD_REQUEST) {
            log.info("ResponseMessage: " + con.getResponseMessage());
            assertEquals("Response code", HttpURLConnection.HTTP_BAD_REQUEST, responseCodeBad);
        }

        log.trace("<testEjbcaHealthHttp()");
    }

}
