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
package org.ejbca.ui.web.admin.rainterface;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.ejbca.core.model.ra.ExtendedInformation;

/**
 * Bean used by JSP pages containing logic for setting and getting end entity
 * data.
 * 
 * Currently only used for extension data.
 *
 * @author Markus Kilas
 * @version $Id: EditEndEntityBean.java 12679 2011-09-22 11:40:29Z netmackan $
 */
public class EditEndEntityBean {
	private ExtendedInformation extendedInformation;

        /**
         * Set the current end entity's ExtendedInformation.
         * @param extendedInformation 
         */
	public void setExtendedInformation(ExtendedInformation extendedInformation) {
		this.extendedInformation = extendedInformation;
	}

        /**
         * Parses certificate extension data from a String of properties in Java 
         * Properties format and store it in the extended information.
         *
         * @param extensionData properties to parse and store.
         * @throws IOException 
         */
	public void setExtensionData(String extensionData) {
		Properties properties = new Properties();
                try {
                    properties.load(new StringReader(extensionData));
                } catch (IOException ex) {
                    // Should not happen as we are only reading from a String.
                    throw new RuntimeException(ex);
                }

		// Remove old extensiondata
		Map data = (Map) extendedInformation.getData();
		for (Object o : data.keySet()) {
			if (o instanceof String) {
				String key = (String) o;
				if (key.startsWith(ExtendedInformation.EXTENSIONDATA)) {
					data.remove(key);
				}
			}
		}

		// Add new extensiondata
		for (Object o : properties.keySet()) {
			if (o instanceof String) {
				String key = (String) o;
				data.put(ExtendedInformation.EXTENSIONDATA + key, properties.getProperty(key));
			}
		}
	}
	
        /**
         * @return The extension data read from the extended information and 
         * formatted as in a Properties file.
         */
	public String getExtensionData() {
		final String result;
		if (extendedInformation == null) {
			result = "";
		} else {
			Map data = (Map) extendedInformation.getData();
			Properties properties = new Properties();
			
			for (Object o : data.keySet()) {
				if (o instanceof String) {
	                String key = (String) o;
	                if (key.startsWith(ExtendedInformation.EXTENSIONDATA)) {
	                    String subKey = key.substring(ExtendedInformation.EXTENSIONDATA.length());
	                    properties.put(subKey, data.get(key));
	                }
				}
				
			}
			
			// Render the properties and remove the first line created by the Properties class.
			StringWriter out = new StringWriter();
                        try {
                            properties.store(out, null);
                        } catch (IOException ex) {
                            // Should not happen as we are using a StringWriter
                            throw new RuntimeException(ex);
                        }
			
			StringBuffer buff = out.getBuffer();
			String lineSeparator = System.getProperty("line.separator");
			int firstLineSeparator = buff.indexOf(lineSeparator);
			
			result = firstLineSeparator >= 0 ? buff.substring(firstLineSeparator + lineSeparator.length()) : buff.toString();
		}
		return result;
	}
        
    /**
     * 
     * @return A Map view of the extension data.
     */
    public Map<String, String> getExtensionDataAsMap() {
        final Map<String, String> result = new HashMap<String, String>();
        if (extendedInformation != null) {
            Map data = (Map) extendedInformation.getData();
            for (Object o : data.keySet()) {
                String key = (String) o;
                if (key.startsWith(ExtendedInformation.EXTENSIONDATA)) {
                    String subKey = key.substring(ExtendedInformation.EXTENSIONDATA.length());
                    result.put(subKey, (String) data.get(key));
                }
            }
        }
        return result;
    }
	
	
}
