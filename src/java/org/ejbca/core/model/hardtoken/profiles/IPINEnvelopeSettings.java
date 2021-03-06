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
 
package org.ejbca.core.model.hardtoken.profiles;

import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.io.IOException;

import org.ejbca.core.model.ra.UserDataVO;






/**
 * Interface contating methods that need to be implementet in order 
 * to have a hard token profile contain PIN Envelope settings.
 * 
 * @version $Id: IPINEnvelopeSettings.java 8373 2009-11-30 14:07:00Z jeklund $
 */

public interface IPINEnvelopeSettings {


	/**
	 * Constant indicating that no envelope should be printed.
	 */    
	public static int PINENVELOPETYPE_NONE = 0;
    /**
     * Constant indicating that a general envelope type should be printed.
     */    
    public static int PINENVELOPETYPE_GENERALENVELOBE = 1;
    
    /**      
     * @return the type of PIN envelope to print.
     */
    public abstract int getPINEnvelopeType();    

	/**      
	 * sets the pin envelope type.
	 */
	public abstract void setPINEnvelopeType(int pinenvelopetype);    
    
    /**
     * @return the filename of the current PIN envelope template.
     */
    public abstract String getPINEnvelopeTemplateFilename();

	/**
	 * Sets the filename of the current PIN envelope template.
	 */    
	public abstract void setPINEnvelopeTemplateFilename(String filename);
    
    /**
     * @return the data of the PIN Envelope template.
     */
    public abstract String getPINEnvelopeData();
    
    /**
     * Sets the data of the PIN envelope template.
     */
    public abstract void setPINEnvelopeData(String data);

    /**
     * @return the number of copies of this PIN Envelope that should be printed.
     */
    public abstract int getNumberOfPINEnvelopeCopies();

	/**
	 * Sets the number of copies of this PIN Envelope that should be printed.
	 */
	public abstract void setNumberOfPINEnvelopeCopies(int copies);

	/**
	 * @return the validity of the visual layout in days.
	 */
	public abstract int getVisualValidity();

	/**
	 * Sets the validity of the visual layout in days.
	 */
	public abstract void setVisualValidity(int validity);

   /**
    * Method that parses the template, replaces the userdata
    * and returning a printable byte array 
    */	
	public abstract Printable printPINEnvelope(UserDataVO userdata, 
	                                        String[] pincodes, String[] pukcodes,
	                                        String hardtokensn, String copyoftokensn) 
	                                          throws   IOException, PrinterException;
}

