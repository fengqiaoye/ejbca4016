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
package org.ejbca.util;

import java.text.ParseException;
import java.util.Date;
import java.util.TimeZone;

import org.apache.commons.lang.time.FastDateFormat;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

/**
 * @version $Id: ValidityDateTest.java 11951 2011-05-11 11:22:00Z jeklund $
 */
public class ValidityDateTest {

	private static final Logger LOG = Logger.getLogger(ValidityDateTest.class);
	private static final String RELATIVE = "relative";
	private static final String ABSOLUTE = "absolute";
	
	/** Since the test will run in different time zones we will test combined operations. */
	@Test
	public void testParseFormat() throws ParseException {
		LOG.trace(">testParseFormat");
		final Date nowWithOutMillis = new Date((new Date().getTime()/1000)*1000);	// We will loose the millis in the conversion
		Assert.assertEquals(nowWithOutMillis, ValidityDate.parseAsIso8601(ValidityDate.formatAsISO8601(nowWithOutMillis, ValidityDate.TIMEZONE_SERVER)));
		final Date zero = new Date(0);
		Assert.assertEquals(zero, ValidityDate.parseAsIso8601(ValidityDate.formatAsISO8601(zero, ValidityDate.TIMEZONE_SERVER)));
		LOG.trace("<testParseFormat");
	}
	
	@Test
	public void testEncodeRelative() {
		LOG.trace(">testEncodeRelative");
		final long ERROR_CODE = -1;
		testEncodeInternal(RELATIVE, "0", ERROR_CODE);
		testEncodeInternal(RELATIVE, "0d", 0);
		testEncodeInternal(RELATIVE, "-1d", ERROR_CODE);
		testEncodeInternal(RELATIVE, "1d", 1);
		testEncodeInternal(RELATIVE, "1d1h1m", ERROR_CODE);
		testEncodeInternal(RELATIVE, "0y0m1d", ERROR_CODE);
		testEncodeInternal(RELATIVE, "0y0mo1d", 1);
		LOG.trace("<testEncodeRelative");
	}
	
	@Test
	public void testEncodeAbsolute() {
		LOG.trace(">testEncodeAbsolute");
		final long ERROR_CODE = -1;
		testEncodeInternal(ABSOLUTE, "yyyy-MM-dd HH:mm:ssZZ", ERROR_CODE);
		testEncodeInternal(ABSOLUTE, "2011-05-09T16:58:00+00:00", ERROR_CODE);
		testEncodeInternal(ABSOLUTE, "2011-05-09 16:58:00+00:00", 1304960280000L);
		LOG.trace("<testEncodeAbsolute");
	}

	private void testEncodeInternal(final String type, final String subject, final long result) {
		Assert.assertEquals("Test of " + type + " date " + subject + " failed.", result, ValidityDate.encode(subject));
	}

	@Test
	public void testGetString() throws ParseException {
		LOG.trace(">testGetString");
		// Test relative times (<Integer.MAX_VALUE)
		testGetStringInternalRel(0, "0d");
		testGetStringInternalRel(1, "1d");
		// Test absolute time (==Integer.MAX_VALUE)
		testGetStringInternalAbs(Integer.valueOf(Integer.MAX_VALUE).longValue(), "1970-01-25 20:31:23+00:00");
		// Test absolute times (>Integer.MAX_VALUE)
		testGetStringInternalAbs(Integer.valueOf(Integer.MAX_VALUE).longValue()+1, "1970-01-25 20:31:23+00:00");
		testGetStringInternalAbs(1304960280000L, "2011-05-09 16:58:00+00:00");
		LOG.trace("<testGetString");
	}

	private void testGetStringInternalRel(final long subject, final String result) throws ParseException {
		Assert.assertEquals("Failed to fetch relative time for " + subject, result, ValidityDate.getString(subject));
	}

	private void testGetStringInternalAbs(final long subject, final String result) throws ParseException {
		Assert.assertEquals("Failed to fetch absolute time for " + subject, ValidityDate.parseAsIso8601(result), ValidityDate.parseAsIso8601(ValidityDate.getString(subject)));
	}
	
	@Test
	public void testGetDate() {
		LOG.trace(">testGetDate");
		final Date now = new Date();
		// Test errors (no error handling available in this method)
		//testGetDateInternal(0, null, null);
		//testGetDateInternal(-1, now, null);
		// Test relative times (<Integer.MAX_VALUE)
		testGetDateInternal(0, now, now);
		testGetDateInternal(1, now, new Date(now.getTime()+24*3600*1000));
		// Test absolute time (==Integer.MAX_VALUE)
		testGetDateInternal(Integer.MAX_VALUE, now, new Date(Integer.MAX_VALUE));
		// Test absolute times (>Integer.MAX_VALUE)
		testGetDateInternal(Integer.valueOf(Integer.MAX_VALUE).longValue()+1, now, new Date(Integer.valueOf(Integer.MAX_VALUE).longValue()+1));
		LOG.trace("<testGetDate");
	}

	private void testGetDateInternal(final long subjectLEncoded, final Date subjectFromDate, final Date result) {
		Assert.assertEquals("Failed to fetch date for " + subjectLEncoded + " and " + subjectFromDate, result, ValidityDate.getDate(subjectLEncoded, subjectFromDate));
	}

	@Test
	public void testGetEncode() {
		LOG.trace(">testGetEncode");
		// Test relative times (<Integer.MAX_VALUE)
		Assert.assertEquals("", 0, ValidityDate.encode(ValidityDate.getString(0)));
		Assert.assertEquals("", 1, ValidityDate.encode(ValidityDate.getString(1)));
		// Test absolute times (>Integer.MAX_VALUE)
		final long nowWithOutSeconds = (new Date().getTime()/60000)*60000;
		Assert.assertEquals("", nowWithOutSeconds, ValidityDate.encode(ValidityDate.getString(nowWithOutSeconds)));
		LOG.trace("<testGetEncode");
	}

	@Test
	public void testEncodeGet() throws ParseException {
		LOG.trace(">testEncodeGet");
		Assert.assertEquals("", ValidityDate.parseAsIso8601("2011-05-09 16:58:00+00:00"), ValidityDate.parseAsIso8601(ValidityDate.getString(ValidityDate.encode("2011-05-09 16:58:00+00:00"))));
		Assert.assertEquals("", ValidityDate.parseAsIso8601("1970-01-25 20:32:00+00:00"), ValidityDate.parseAsIso8601(ValidityDate.getString(ValidityDate.encode("1970-01-25 20:32:00+00:00"))));
		LOG.trace("<testEncodeGet");
	}

	/** Test the Date the feature was designed for (http://en.wikipedia.org/wiki/Year_2038_problem) */
	@Test
	public void testParseCaLatestValidDateTime() {
		LOG.trace(">testParseCaLatestValidDateTime");
		final String bug2038Hex = "80000000";
		LOG.info("bug2038Hex: " + bug2038Hex);
		final String bug2038Iso = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ssZZ", TimeZone.getTimeZone("UTC")).format(Long.parseLong("80000000", 16)*1000);
		LOG.info("bug2038Iso: " + bug2038Iso);
		final Date bug2038HexDate = ValidityDate.parseCaLatestValidDateTime(bug2038Hex);
		LOG.info("bug2038HexDate: " + bug2038HexDate);
		final Date bug2038IsoDate = ValidityDate.parseCaLatestValidDateTime(bug2038Iso);
		LOG.info("bug2038IsoDate: " + bug2038IsoDate);
		Assert.assertEquals("The two date formats should yield the same Date!", bug2038HexDate, bug2038IsoDate);
		// Test now also
		final Date now = new Date();
		LOG.info("now:        " + now);
		final String nowIso = FastDateFormat.getInstance(ValidityDate.ISO8601_DATE_FORMAT, TimeZone.getTimeZone("UTC")).format(now);
		LOG.info("nowIso:     " + nowIso);
		final Date nowIsoDate = ValidityDate.parseCaLatestValidDateTime(nowIso);
		LOG.info("nowIsoDate: " + nowIsoDate);
		// Compare as strings since we will loose milliseconds in the conversion to ISO8601 format
		Assert.assertEquals("Unable to parse current time correctly!", now.toString(), nowIsoDate.toString());
		// Test unhappy path (return of default value)
		final Date defaultIsoDate = ValidityDate.parseCaLatestValidDateTime("COFFEE");
		Assert.assertEquals("Default value not returned when invalid date-time specified!", new Date(Long.MAX_VALUE).toString(), defaultIsoDate.toString());
		LOG.trace("<testParseCaLatestValidDateTime");
	}
}
