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

package org.ejbca.core.model.ca.certextensions;

import java.math.BigInteger;
import java.security.PublicKey;
import java.util.Arrays;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DERBoolean;
import org.bouncycastle.asn1.DEREncodable;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.DERInteger;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DERObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERPrintableString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.util.encoders.Hex;
import org.ejbca.core.model.InternalResources;
import org.ejbca.core.model.ca.caadmin.CA;
import org.ejbca.core.model.ca.certificateprofiles.CertificateProfile;
import org.ejbca.core.model.ra.ExtendedInformation;
import org.ejbca.core.model.ra.UserDataVO;

/**
 * The default basic certificate extension that has two property.
 * 
 * 'value'    : The value returned
 * 'encoding' : How the value is encoded.
 * 
 * Optionally, a new property can be defined:
 *
 * 'nvalues' : number of values of type 'encoding'
 *
 * Thus, the extension will be of type 'SEQUENCE OF ENCODING'
 * with a size of nvalues. The members will be:
 *  'value1', 'value2' and so on.
 *  
 * Optionally, an other property can be defined:
 * 
 *  'dynamic' : true/false if the extension value(s) should be allowed to be 
 *              overridden by value(s) put as extensiondata in 
 *              ExtendedInformation. Default is 'false'.
 *
 *
 * See documentation for more information.
 * 
 * @author Philip Vendil 2007 jan 5
 * @author Miguel Tormo  2008 oct 24
 *
 * @version $Id: BasicCertificateExtension.java 12665 2011-09-21 14:28:33Z netmackan $
 */

public class BasicCertificateExtension extends CertificateExtension {

	private static final Logger log = Logger.getLogger(BasicCertificateExtension.class);
	
	private static final InternalResources intres = InternalResources.getInstance();
	
	private static String ENCODING_DERBITSTRING       = "DERBITSTRING";	 
	private static String ENCODING_DERINTEGER         = "DERINTEGER";
	private static String ENCODING_DEROCTETSTRING	  = "DEROCTETSTRING";
	private static String ENCODING_DERBOOLEAN         = "DERBOOLEAN";	      
	private static String ENCODING_DERPRINTABLESTRING = "DERPRINTABLESTRING";	 
	private static String ENCODING_DERUTF8STRING      = "DERUTF8STRING";	 
	private static String ENCODING_DERIA5STRING       = "DERIA5STRING";	 
	private static String ENCODING_DERNULL            = "DERNULL";
	private static String ENCODING_DEROBJECT          = "DEROBJECT";
	private static String ENCODING_DEROID             = "DERBOJECTIDENTIFIER";
	
	/** 
     * The value is expected to by hex encoded and is added as an byte array 
     * as the extension value. 
     **/
	private static String ENCODING_RAW                = "RAW";
	
	// Defined Properties
	private static String PROPERTY_VALUE    = "value";
	private static String PROPERTY_ENCODING = "encoding";
    private static String PROPERTY_NVALUES  = "nvalues";
    private static String PROPERTY_DYNAMIC  = "dynamic";
	
	/**
	 * Returns the defined property 'value' in the encoding 
	 * specified in 'encoding'.
	 * 
	 * @param userData not used
	 * @param ca not used
	 * @param certProfile not used
	 * @see org.ejbca.core.model.ca.certextensions.CertificateExtension#getValue(org.ejbca.core.model.ra.UserDataVO, org.ejbca.core.model.ca.caadmin.CA, org.ejbca.core.model.ca.certificateprofiles.CertificateProfile, PublicKey)
	 */
	private DEREncodable parseValue(String encoding, String value) throws CertificateExtentionConfigurationException, CertificateExtensionException {

		DEREncodable toret = null;

		if(!encoding.equalsIgnoreCase(ENCODING_DERNULL) && (value == null || value.trim().equals(""))){
			throw new CertificateExtentionConfigurationException(intres.getLocalizedMessage("certext.basic.incorrectvalue", Integer.valueOf(getId()), getOID()));
		}

		if(encoding.equalsIgnoreCase(ENCODING_DERBITSTRING)){
			toret = parseDERBitString(value);
		}else
			if(encoding.equalsIgnoreCase(ENCODING_DERINTEGER)){
				toret = parseDERInteger(value);
			}else
				if(encoding.equalsIgnoreCase(ENCODING_DEROCTETSTRING)){
					toret = parseDEROctetString(value);
				}else
					if(encoding.equalsIgnoreCase(ENCODING_DERBOOLEAN)){
						toret = parseDERBoolean(value);
					}else 
						if(encoding.equalsIgnoreCase(ENCODING_DEROID)){
							toret = parseDEROID(value);
						} else
							if(encoding.equalsIgnoreCase(ENCODING_DERPRINTABLESTRING)){
								toret = parseDERPrintableString(value);
							}else
								if(encoding.equalsIgnoreCase(ENCODING_DERUTF8STRING)){
									toret = parseDERUTF8String(value);
								}else
									if(encoding.equalsIgnoreCase(ENCODING_DERIA5STRING)){
										toret = parseDERIA5String(value);
									}else
										if(encoding.equalsIgnoreCase(ENCODING_DERNULL)){
											toret = new DERNull();
										}else
											if(encoding.equalsIgnoreCase(ENCODING_DEROBJECT)){
												toret = parseHexEncodedDERObject(value);
											}else
												if(encoding.equalsIgnoreCase(ENCODING_DEROID)){
													toret = parseDERBoolean(value);
												}else{
												throw new CertificateExtentionConfigurationException(intres.getLocalizedMessage("certext.basic.incorrectenc", encoding, Integer.valueOf(getId())));
											}
		return toret;
	}

        /**
         * @deprecated use getValueEncoded instead.
         */
	public DEREncodable getValue(UserDataVO userData, CA ca, CertificateProfile certProfile, PublicKey userPublicKey, PublicKey caPublicKey)
	throws CertificateExtensionException, CertificateExtentionConfigurationException {
		throw new UnsupportedOperationException("Use getValueEncoded instead");
	}
	
	/**
	 * This certificate extension implementations overrides this method as it 
	 * want to be able to return a byte[] with the extension value. Otherwise 
	 * the implementation could have been put in the getValue method as the 
	 * super class CertificateExtension has a default implementation for 
	 * getValueEncoded which calls getValue.
	 * @see CertificateExtension#getValueEncoded(UserDataVO, CA, CertificateProfile, PublicKey, PublicKey) 
	 */
	@Override
	public byte[] getValueEncoded(UserDataVO userData, CA ca, CertificateProfile certProfile, PublicKey userPublicKey, PublicKey caPublicKey)
	throws CertificateExtensionException, CertificateExtentionConfigurationException {
		final byte[] result;
			String encoding = StringUtils.trim(getProperties().getProperty(PROPERTY_ENCODING));
			String[] values = getValues(userData);
			if (log.isDebugEnabled()) {
				log.debug("Got extension values: " + Arrays.toString(values));
			}
			
			if (values == null || values.length == 0) {
                            throw new CertificateExtentionConfigurationException(intres.getLocalizedMessage("certext.basic.incorrectvalue", Integer.valueOf(getId()), getOID()));
                        }
			
			if (encoding.equalsIgnoreCase(ENCODING_RAW)) {
                            if (values.length > 1) {
                                // nvalues can not be used together with encoding=RAW
                                throw new CertificateExtentionConfigurationException(intres.getLocalizedMessage("certext.certextmissconfigured", Integer.valueOf(getId())));
                            } else {
				result = parseRaw(values[0]);
                            }
			} else {
				if (values.length > 1) {
					ASN1EncodableVector ev = new ASN1EncodableVector();
					for (String value : values) {
						DEREncodable derval = parseValue(encoding, value);
						ev.add(derval);
					}
					result = new DERSequence(ev).getDEREncoded();
				} else {
					result = parseValue(encoding, values[0]).getDERObject().getDEREncoded();
				}
			}
		return result;
	}
	
	/**
	 * Get the extension value by first looking in the ExtendedInformation (if 
	 * dynamic is enabled) and then in the static configuration.
	 * 
	 * @param userData The userdata to get the ExtendedInformation from
	 * @return The value(s) for the extension (usually 1) or null if no value found
	 */
	private String[] getValues(UserDataVO userData) {
		String[] result = null;
		
		boolean dynamic = Boolean.parseBoolean(StringUtils.trim(getProperties().getProperty(PROPERTY_DYNAMIC, Boolean.FALSE.toString())));
		
		String strnvalues = getProperties().getProperty(PROPERTY_NVALUES);

		int nvalues;

		if ( strnvalues == null || strnvalues.trim().equals("") ) {
			nvalues = 0;
		} else {
			nvalues = Integer.parseInt(strnvalues);
		}
		
		if (dynamic) {
			final ExtendedInformation ei = userData.getExtendedinformation();
			if (ei == null) {
				result = null;
			} else {
				if (nvalues < 1 ) {
					String value = userData.getExtendedinformation().getExtensionData(getOID());
					if (value == null || value.trim().isEmpty()) {
						value = userData.getExtendedinformation().getExtensionData(getOID() + "." + PROPERTY_VALUE);
					}
					if (value == null) {
						result = null;
					} else {
						result = new String[] { value };
					}
				} else {
					for (int i = 1; i <= nvalues; i++) {
						 String value = userData.getExtendedinformation().getExtensionData(getOID() + "." + PROPERTY_VALUE + Integer.toString(i));
						 if (value != null) {
							 if (result == null) {
								result = new String[nvalues];
							 }
							 result[i - 1] = value;
						 }
					}
				}
			}
		}
		if (result == null) {
			if (nvalues < 1 ) {
				String value = getProperties().getProperty(PROPERTY_VALUE);
				if ( value == null || value.trim().equals("") ) {
					value = getProperties().getProperty(PROPERTY_VALUE+"1");
				}
                                    result = new String[] { value };
			} else {
				result = new String[nvalues];
				for (int i=1; i<=nvalues; i++) {
					result[i - 1] = getProperties().getProperty(PROPERTY_VALUE+Integer.toString(i));
				}
			}
		}
		return result;
	}

    private DEREncodable parseDERBitString(String value) throws CertificateExtentionConfigurationException {
		DEREncodable retval = null;
		try{
			BigInteger bigInteger = new BigInteger(value,2);			
			int padBits = value.length() - 1 - value.lastIndexOf("1");
			if(padBits == 8){
				padBits = 0;
			}
			byte[] byteArray = bigInteger.toByteArray();
			if (byteArray[0] == 0) { 
				// Remove empty extra byte
				byte[] shorterByteArray = new byte[byteArray.length-1];
				for (int i=0; i<shorterByteArray.length; i++) {
					shorterByteArray[i] = byteArray[i+1];
				}
				byteArray = shorterByteArray; 
			}
			retval = new DERBitString(byteArray, padBits);
		}catch(NumberFormatException e){
                    throw new CertificateExtentionConfigurationException(intres.getLocalizedMessage("certext.basic.illegalvalue",value,Integer.valueOf(getId()), getOID()));
		}
		
		return retval;
	}
	
    private DEREncodable parseDEROID(String value) throws CertificateExtentionConfigurationException {
    	DEREncodable retval = null;
        try{
            retval = new DERObjectIdentifier(value);
        }catch(Exception e){
            throw new CertificateExtentionConfigurationException(intres.getLocalizedMessage("certext.basic.illegalvalue",value,Integer.valueOf(getId()), getOID()));
        }

        return retval;
    }

	private DEREncodable parseDERInteger(String value) throws CertificateExtentionConfigurationException {
		DEREncodable retval = null; 
		try{
			BigInteger intValue = new BigInteger(value,10);
			retval = new DERInteger(intValue);
		}catch(NumberFormatException e){
			throw new CertificateExtentionConfigurationException(intres.getLocalizedMessage("certext.basic.illegalvalue",value,Integer.valueOf(getId()), getOID()));
		}

		return retval;
	}
	
	private DEREncodable parseDEROctetString(String value) throws CertificateExtentionConfigurationException {
		DEREncodable retval = null;
		if(value.matches("^\\p{XDigit}*")){		  
		  byte[] bytes = Hex.decode(value);
		  retval = new DEROctetString(bytes);
		}else{		
                    throw new CertificateExtentionConfigurationException(intres.getLocalizedMessage("certext.basic.illegalvalue",value,Integer.valueOf(getId()), getOID()));
		}
		return retval;
	}

	/**
	 * Tries to read the hex-string as an DERObject. If it contains more than one DEREncodable object, return a DERSequence of the objects.
	 */
	private DEREncodable parseHexEncodedDERObject(String value) throws CertificateExtentionConfigurationException {
		DEREncodable retval = null;
		if(value.matches("^\\p{XDigit}*")){		  
		  byte[] bytes = Hex.decode(value);
		  try {
			  ASN1InputStream ais = new ASN1InputStream(bytes);
			  DEREncodable firstObject = ais.readObject();
			  if (ais.available() > 0) {
				  ASN1EncodableVector ev = new ASN1EncodableVector();
				  ev.add(firstObject);
				  while (ais.available() > 0) {
					  ev.add(ais.readObject());
				  }
				  retval = new DERSequence(ev);
			  } else {
				  retval = firstObject;
			  }
		  } catch (Exception e) {
                        throw new CertificateExtentionConfigurationException(intres.getLocalizedMessage("certext.basic.illegalvalue",value,Integer.valueOf(getId()), getOID()));
		  }
		}else{		
                    throw new CertificateExtentionConfigurationException(intres.getLocalizedMessage("certext.basic.illegalvalue",value,Integer.valueOf(getId()), getOID()));
		}
		return retval;
	}

	private DEREncodable parseDERBoolean(String value) throws CertificateExtentionConfigurationException {
		DEREncodable retval = null;
		if(value.equalsIgnoreCase("TRUE")){
			retval = DERBoolean.TRUE;
		}
		
		if(value.equalsIgnoreCase("FALSE")){
			retval = DERBoolean.FALSE;
		}
		
		if(retval == null){
                    throw new CertificateExtentionConfigurationException(intres.getLocalizedMessage("certext.basic.illegalvalue",value,Integer.valueOf(getId()), getOID()));
		}

		return retval;
	}
	
	private DEREncodable parseDERPrintableString(String value) throws CertificateExtentionConfigurationException {
		try{
		  return new DERPrintableString(value,true);
		}catch(java.lang.IllegalArgumentException e){
                    throw new CertificateExtentionConfigurationException(intres.getLocalizedMessage("certext.basic.illegalvalue",value,Integer.valueOf(getId()), getOID()));
		}
	}
	
	private DEREncodable parseDERUTF8String(String value)  {		
		return new DERUTF8String(value);
	}
	
	private DEREncodable parseDERIA5String(String value)  throws CertificateExtentionConfigurationException {		
		try{
			return new DERIA5String(value, true);
		}catch(java.lang.IllegalArgumentException e){
                    throw new CertificateExtentionConfigurationException(intres.getLocalizedMessage("certext.basic.illegalvalue",value,Integer.valueOf(getId()), getOID()));
		}
	}
	
	private byte[] parseRaw(String value) throws CertificateExtentionConfigurationException {
            if(value == null) {
                throw new CertificateExtentionConfigurationException(intres.getLocalizedMessage("certext.basic.incorrectvalue", Integer.valueOf(getId()), getOID()));
            }
		return Hex.decode(value);
	}

}
