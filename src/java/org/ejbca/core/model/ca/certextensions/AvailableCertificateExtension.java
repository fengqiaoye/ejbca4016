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

/**
 * Value object class used to display information about
 * available extensions in the edit certificate profile 
 * page 
 * 
 * 
 * @author Philip Vendil 2007 jan 5
 *
 * @version $Id: AvailableCertificateExtension.java 8373 2009-11-30 14:07:00Z jeklund $
 */

public class AvailableCertificateExtension {

	private int id;
	private String oID;
	private String displayName;
	private boolean translatable;
	

	/**
	 * Default constructor used to create one instance of the object
	 * @param id the id of the extension defined in the configuration file.
	 * @param oid the unique OID string of the extension
	 * @param displayName The name displayed in the GUI
	 * @param used if the extension should be used or hidden
	 * @param translatable if the displayName should be looked up in the language resources.
	 */
	AvailableCertificateExtension(int id, String oid, String displayName, boolean translatable) {
		super();
		this.id = id;
		oID = oid;
		this.displayName = displayName;		
		this.translatable = translatable;
	}
	
	/**
	 * @return The name displayed in the GUI
	 */
	public String getDisplayName() {
		return displayName;
	}

	/**
	 * @return the id of the extension defined in the configuration file.
	 */
	public int getId() {
		return id;
	}

	/**
	 * @return the unique OID string of the extension
	 */
	public String getOID() {
		return oID;
	}

	/**
	 * @return if the extension should be used or hidden
	 */
	public boolean isTranslatable() {
		return translatable;
	}


}
