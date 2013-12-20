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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

import org.hibernate.cfg.AnnotationConfiguration;
import org.hibernate.dialect.Dialect;
import org.hibernate.ejb.Ejb3Configuration;

/**
 * Helper class to generate SQL create/drop scripts using Hibernate. 
 * 
 * @version $Id: DatabaseSchemaScriptCreator.java 15009 2012-06-18 12:49:30Z primelars $
 */
public class DatabaseSchemaScriptCreator {

    public static void main(String[] args) {
    	System.out.println("Executed with " + Arrays.toString(args));
		final String dropScriptFileName = args[0] + File.separator + "drop-tables-ejbca4-" + args[1] + ".sql";
    	final String createScriptFileName = args[0] + File.separator + "create-tables-ejbca4-" + args[1] + ".sql";
    	final String createScriptFileNameNdb = args[0] + File.separator + "create-tables-ejbca4-" + args[1] + "-ndbcluster.sql";
    	// Configure with our current persistence unit
    	final Ejb3Configuration ejb3Configuration = new Ejb3Configuration().configure("ejbca-pu", null);
    	final AnnotationConfiguration hibernateConfiguration = ejb3Configuration.getHibernateConfiguration();
		try {
			// Create drop script
	    	final String[] dropScript = hibernateConfiguration.generateDropSchemaScript(Dialect.getDialect(ejb3Configuration.getProperties()));
			StringBuilder sb = new StringBuilder();
			for (String line : dropScript) {
				sb.append(line);
				sb.append(";\n");
			}
			System.out.println("Writing drop script to " + dropScriptFileName);
			new FileOutputStream(dropScriptFileName).write(sb.toString().getBytes());
			// Create create script(s)
			final String[] createScript = hibernateConfiguration.generateSchemaCreationScript(Dialect.getDialect(ejb3Configuration.getProperties()));
			sb = new StringBuilder();
			for (String line : createScript) {
				// Format nicely, so it looks more like the old, manually created ones.
				if (line.startsWith("create")) {
					line = line.replaceAll("create table", "CREATE TABLE");
					line = line.replaceAll("primary key", "PRIMARY KEY");
					line = line.replaceAll("not null", "NOT NULL");
					line = line.replaceAll("Data \\(", "Data \\(\n    ");
					line = line.replaceAll("Map \\(", "Map \\(\n    ");
					line = line.replaceAll(", ", ",\n    ");
				}
				line += ";\n\n";
				line = line.replaceAll("\\)\\);", "\\)\n\\);");
				sb.append(line);
			}
			System.out.println("Writing create script to " + createScriptFileName);
			new FileOutputStream(createScriptFileName).write(sb.toString().getBytes());
			if (args[1].equals("mysql")) {
				sb.insert(0, "-- This script assumes that the tablespace 'ejbca_ts' exists.\n\n");
				System.out.println("Writing create script to " + createScriptFileNameNdb);
				new FileOutputStream(createScriptFileNameNdb).write(sb.toString().replaceAll("\n\\);\n", "\n\\) TABLESPACE ejbca_ts STORAGE DISK ENGINE=NDB;\n").getBytes());
			}
			//String[] updateScript = hibernateConfiguration.generateSchemaUpdateScript(Dialect.getDialect(ejb3Configuration.getProperties(), ...));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }

}
