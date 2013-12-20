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
package org.ejbca.core.model.ra;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.naming.InvalidNameException;
import javax.naming.ldap.Rdn;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.ejbca.core.model.ra.raadmin.EndEntityProfile;
import org.ejbca.util.CertTools;
import org.ejbca.util.dn.DNFieldExtractor;
import org.ejbca.util.dn.DistinguishedName;
import org.ejbca.util.dn.DnComponents;

/** This class gives facilities to populate user data with default values from profile.
 *
 * @author David Galichet
 * @version $Id: UserDataFiller.java 10397 2010-11-08 14:18:57Z anatom $
 */
public class UserDataFiller {

    /** For log purpose. */
    private static final Logger log = Logger.getLogger(UserDataFiller.class.getName());

    /** This method fill user data with the default values from the specified profile.
     * 
     * @param userData user data.
     * @param profile user associated profile.
     * @return update user.
     */
    public static UserDataVO fillUserDataWithDefaultValues(UserDataVO userData, EndEntityProfile profile) {

    	
    	if (StringUtils.isEmpty(userData.getUsername())) {
        	userData.setUsername(profile.getValue(EndEntityProfile.USERNAME, 0));
        }
    	if (userData.getSendNotification()==false) {
    		if(StringUtils.isNotEmpty(profile.getValue(EndEntityProfile.SENDNOTIFICATION, 0))) {
    			Boolean isSendNotification = new Boolean(profile.getValue(EndEntityProfile.SENDNOTIFICATION, 0));
    			userData.setSendNotification(isSendNotification.booleanValue());    			
    		}
        }
    	if (StringUtils.isEmpty(userData.getEmail())) {
			String email = profile.getValue(EndEntityProfile.EMAIL, 0);
			if (StringUtils.isNotEmpty(email) && email.indexOf("@") > 0) {
				userData.setEmail(email);
			}
		}
        //Batch generation (clear text pwd storage) is only active when password 
        //is not empty so is not necessary to do something here
        if (StringUtils.isEmpty(userData.getPassword())) {
            // check if the password is autogenerated
        	if(!profile.useAutoGeneratedPasswd()) {
        		userData.setPassword(profile.getValue(EndEntityProfile.PASSWORD, 0));        		
        	}
        }
        
        // Processing Subject DN values
        String subjectDN = userData.getDN();
        subjectDN = mergeSubjectDnWithDefaultValues(subjectDN, profile, userData.getEmail());
        userData.setDN(subjectDN);
        String subjectAltName = userData.getSubjectAltName();
        subjectAltName = mergeSubjectAltNameWithDefaultValues(subjectAltName, profile, userData.getEmail());
        userData.setSubjectAltName(subjectAltName);
        if (userData.getType()==0) {
        	if(StringUtils.isNotEmpty(profile.getValue(EndEntityProfile.FIELDTYPE, 0))){
        		userData.setType(Integer.valueOf(profile.getValue(EndEntityProfile.FIELDTYPE, 0)).intValue());
        	}
        }
        return userData;
    }

    /** This method merge subject DN with data from End entity profile.
     * @param subjectDN user Distinguished Name.
     * @param profile user associated profile.
     * @param email entity email.
     * @return updated DN.
     */
    private static String mergeSubjectDnWithDefaultValues(String subjectDN, EndEntityProfile profile, 
            String entityEmail) {
        DistinguishedName profiledn;
        DistinguishedName userdn;
        try {
        	userdn = new DistinguishedName(subjectDN);
		} catch (InvalidNameException ine) {
			log.debug(subjectDN,ine);
			throw new RuntimeException(ine);
		}
        int numberofsubjectdnfields = profile.getSubjectDNFieldOrderLength();
        List rdnList = new ArrayList(numberofsubjectdnfields);
        int[] fielddata = null;
        String value;
        //Build profile's DN
        for (int i = 0; i < numberofsubjectdnfields; i++) {
        	value=null;
			fielddata = profile.getSubjectDNFieldsInOrder(i);
			String parameter = DNFieldExtractor.getFieldComponent(
					DnComponents.profileIdToDnId(fielddata[EndEntityProfile.FIELDTYPE]),
					DNFieldExtractor.TYPE_SUBJECTDN);
			value = profile.getValue(fielddata[EndEntityProfile.FIELDTYPE], 0);
			if (value != null) {
				value = value.trim();
				if (!value.equals("")) {					
					try {
						parameter = StringUtils.replace(parameter, "=", "");
						rdnList.add(fielddata[EndEntityProfile.NUMBER],new Rdn(parameter,value));
					}catch(InvalidNameException ine) {
						log.debug("InvalidNameException while creating new Rdn with parameter "+ parameter + " and value " + value,ine);
						throw new RuntimeException(ine);
					}
					
				}
			}
		}
        profiledn = new DistinguishedName(rdnList);

        Map dnMap = new HashMap();
        if (profile.getUse(DnComponents.DNEMAIL, 0)) {
            dnMap.put(DnComponents.DNEMAIL, entityEmail);
        }
//        return  profiledn.mergeDN(userdn, true, dnMap).toString();
        return  CertTools.stringToBCDNString(profiledn.mergeDN(userdn, true, dnMap).toString());
    }
 
    /**
     * This method merge subject Alt name with data from End entity profile.
     * @param subjectAltName user subject alt name.
     * @param profile user associated profile.
     * @param email entity email field
     * @return updated subject alt name
     */
    private static String mergeSubjectAltNameWithDefaultValues(String subjectAltName, EndEntityProfile profile, String entityEmail) {
        DistinguishedName profileAltName;
        DistinguishedName userAltName;
        try {
        	if(subjectAltName==null) {
        		subjectAltName = "";
        	}
        	userAltName = new DistinguishedName(subjectAltName);
		} catch (InvalidNameException ine) {
			log.debug(subjectAltName,ine);
			throw new RuntimeException(ine);
		}
        int numberofsubjectAltNamefields = profile.getSubjectAltNameFieldOrderLength();
        List rdnList = new ArrayList(numberofsubjectAltNamefields);
        int[] fielddata = null;
        String value;
        //Build profile's Alt Name
        for (int i = 0; i < numberofsubjectAltNamefields; i++) {
        	value=null;
			fielddata = profile.getSubjectAltNameFieldsInOrder(i);
			String parameter = DNFieldExtractor.getFieldComponent(
					DnComponents.profileIdToDnId(fielddata[EndEntityProfile.FIELDTYPE]),
					DNFieldExtractor.TYPE_SUBJECTALTNAME);
			value = profile.getValue(fielddata[EndEntityProfile.FIELDTYPE], 0);
			if (value != null) {
				value = value.trim();
				if (!value.equals("")) {					
					try {
						parameter = StringUtils.replace(parameter, "=", "");
						rdnList.add(fielddata[EndEntityProfile.NUMBER],new Rdn(parameter,value));
					}catch(InvalidNameException ine) {
						log.debug("InvalidNameException while creating new Rdn with parameter "+ parameter + " and value " + value,ine);
						throw new RuntimeException(ine);
					}
					
				}
			}
		}
        profileAltName = new DistinguishedName(rdnList);

        Map dnMap = new HashMap();
        if (profile.getUse(DnComponents.RFC822NAME, 0)) {
            dnMap.put(DnComponents.RFC822NAME, entityEmail);
        }
//        return  profileAltName.mergeDN(userAltName, true, dnMap).toString();
        return  CertTools.stringToBCDNString(profileAltName.mergeDN(userAltName, true, dnMap).toString());
    }
}