package org.ejbca.ui.web.pub.cluster;

import java.net.URL;

import org.apache.log4j.Logger;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebConnection;
import com.gargoylesoftware.htmlunit.WebRequestSettings;
import com.gargoylesoftware.htmlunit.WebResponse;

/**
 *
 * @version $Id: WebOcspHealthCheckTest.java 13423 2011-12-19 13:04:32Z jeklund $
 */
public class WebOcspHealthCheckTest extends WebHealthTestAbstract {
    private static final Logger log = Logger.getLogger(WebOcspHealthCheckTest.class);

    /**
     * Creates a new TestSignSession object.
     *
     * @param name name
     */
    public WebOcspHealthCheckTest(String name) {
        super(name);
        httpPort = "8080"; 
        httpReqPath = "http://localhost:" + httpPort + "/ejbca/publicweb/vahealthcheck/vahealth";
    }

    public void setUp() throws Exception {
    }

    public void tearDown() throws Exception {
    }

    /**
     * Creates a number of threads that bombards the health check servlet 1000
     * times each
     */
    public void testEjbcaHealthHttp() throws Exception {
        log.trace(">testEjbcaHealthHttp()");
        // Make a quick test first that it works at all before starting all threads
        final WebClient webClient = new WebClient();
		webClient.setTimeout(31*1000);
        WebConnection con = webClient.getWebConnection();
        WebRequestSettings settings = new WebRequestSettings(new URL(httpReqPath));
        WebResponse resp = con.getResponse(settings);
        final int statusCode = resp.getStatusCode();
        final String contentAsString = resp.getContentAsString();
        log.debug("Response: statusCode=" + statusCode + " contentAsString=" + contentAsString);
        assertEquals("Response code", 200, statusCode);
        assertEquals("ALLOK", contentAsString);
        long before = System.currentTimeMillis();
        createThreads();
        long after = System.currentTimeMillis();
        long diff = after - before;
        log.info("All threads finished. Total time: " + diff + " ms");
        assertTrue("Healt check test(s) timed out!", diff < 30L*1000L);
        log.trace("<testEjbcaHealthHttp()");
    }

}

