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

package org.ejbca.util.dn;

import junit.framework.TestCase;

/**
 * @author primelars
 * @version $Id: DNFieldsUtilTest.java 11880 2011-05-03 13:48:00Z jeklund $
 */
public class DNFieldsUtilTest extends TestCase {
    final private static String trickyValue1=" 10/2=5; 2 backs and a comma\\\\\\\\\\, 8/2=4 2 backs\\\\\\\\";// last comma is end of value since it is a even number (4) of \ before
    final private static String trickyValue2="\\,";// a single comma
    final private static String trickyValue3="\\\\\\\\\\\\\\,";// 3 backs and a comma
    final private static String trickyValue4="\\\\\\\\\\\\";// 3 backs
    final private static String trickyValue5="\\,\\\\\\\\\\\\\\,";// comma 3 backs comma
    final private static String trickyValue6="\\,\\\\\\\\\\\\";// comma 3 backs
    final private static String trickyValue7="\\,\\,\\,\\,\\,\\,";// 6 commas
    final private static String trickyValue8="\\\\\\,\\,\\,\\\\\\,\\,\\,\\\\";// 1 back, 3 commas, 1 back, 3 commas, 1 back
    final private static String key1 = "key1=";
    final private static String key2 = "key2=";
    final private static String c = ",";
    final private static String cKey1 = c+key1;
    final private static String cKey2 = c+key2;
    final private static String empty1 = key1+c;
    final private static String empty2 = key2+c;
    final private static String originalDN = key2+trickyValue4+c+empty1+empty2+empty1+empty1+key1+trickyValue1+c+empty1+key2+trickyValue5+c+empty1+empty2+key1+trickyValue2+cKey2+trickyValue6+c+empty1+key2+trickyValue7+cKey1+trickyValue3+c+empty1+empty2+empty1+key2+trickyValue8+c+empty1+empty2+empty2+empty1+empty1+empty2+empty1+key2;
    final private static String trailingSpacesRemovedDN = key2+trickyValue4+c+empty1+empty2+empty1+empty1+key1+trickyValue1+c+empty1+key2+trickyValue5+c+empty1+empty2+key1+trickyValue2+cKey2+trickyValue6+c+empty1+key2+trickyValue7+cKey1+trickyValue3+c+empty2+key2+trickyValue8;
    final private static String allSpacesRemovedDN = key2+trickyValue4+cKey1+trickyValue1+cKey2+trickyValue5+cKey1+trickyValue2+cKey2+trickyValue6+cKey2+trickyValue7+cKey1+trickyValue3+cKey2+trickyValue8;
    final private static String defaultEmptyBefore = "UNSTRUCTUREDNAME=, DN=, POSTALADDRESS=, NAME=, UID=, OU=, 1.3.6.1.4.1.18838.1.1=, 1.3.6.1.4.1.4710.1.3.2=, ST=, UNSTRUCTUREDADDRESS=, BUSINESSCATEGORY=, STREET=, CN=test1, POSTALCODE=, O=, PSEUDONYM=, DC=, SURNAME=, C=, INITIALS=, SN=, L=, GIVENNAME=, TELEPHONENUMBER=, T=, DC=";
    final private static String defaultEmptyAfter = "CN=test1";
    final private static String simpleBeforeAfter = "CN=userName,O=linagora";
    final private static String simple2Before = "CN=userName,O=, O=linagora, O=";
    final private static String simple2AfterA = "CN=userName,O=linagora";
    final private static String simple2AfterT = "CN=userName,O=, O=linagora";

    public void test01removeAllEmpties() {
    	assertEquals(allSpacesRemovedDN, removeEmpties(originalDN, false));
    	assertEquals(defaultEmptyAfter, removeEmpties(defaultEmptyBefore, false));
    	assertEquals(simpleBeforeAfter, removeEmpties(simpleBeforeAfter, false));
    	assertEquals(simple2AfterA, removeEmpties(simple2Before, false));
    }
    public void test02removeTrailingEmpties() {
    	assertEquals(trailingSpacesRemovedDN, removeEmpties(originalDN, true));
    	assertEquals(defaultEmptyAfter, removeEmpties(defaultEmptyBefore, true));
    	assertEquals(simpleBeforeAfter, removeEmpties(simpleBeforeAfter, true));
    	assertEquals(simple2AfterT, removeEmpties(simple2Before, true));
    }
    
    private String removeEmpties(String dn, boolean onlyTrailing) {
    	final StringBuilder sb2 = new StringBuilder();
    	final StringBuilder sb1 = DNFieldsUtil.removeEmpties(dn, sb2, true);
    	final String removedEmpties1 = DNFieldsUtil.removeAllEmpties(dn);
    	final String removedEmpties2 = sb2.toString();
		assertEquals(removedEmpties1, removedEmpties2);
    	if (sb1 == null) {
    		return removedEmpties2;
    	}
    	if (onlyTrailing) {
    		return sb1.toString();
    	}
		return removedEmpties2;
    }
}
