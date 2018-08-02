//////////////////////////////////////////////////////////////////////////////////////////
//
//Copyright (c) 2018 Jamf.  All rights reserved.
//
//      Redistribution and use in source and binary forms, with or without
//      modification, are permitted provided that the following conditions are met:
//              * Redistributions of source code must retain the above copyright
//                notice, this list of conditions and the following disclaimer.
//              * Redistributions in binary form must reproduce the above copyright
//                notice, this list of conditions and the following disclaimer in the
//                documentation and/or other materials provided with the distribution.
//              * Neither the name of the Jamf nor the names of its contributors may be
//                used to endorse or promote products derived from this software without
//                specific prior written permission.
//
//      THIS SOFTWARE IS PROVIDED BY JAMF SOFTWARE, LLC "AS IS" AND ANY
//      EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
//      WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
//      DISCLAIMED. IN NO EVENT SHALL JAMF SOFTWARE, LLC BE LIABLE FOR ANY
//      DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
//      (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
//      LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
//      ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
//      (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
//      SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//
//////////////////////////////////////////////////////////////////////////////////////////
//
// SUPPORT FOR THIS PROGRAM
//
//      This program is distributed "as is".
//
//////////////////////////////////////////////////////////////////////////////////////////
//
// ABOUT THIS PROGRAM
//
// NAME - addPrinters.java
// 
// DESCRIPTION - Adds printers to a Jamf Pro server through the API.  Need to provide the 
//               script a comma delimited files with the printer attributes.
// 	
//////////////////////////////////////////////////////////////////////////////////////////
//
// HISTORY
//
//	Version: 1.0
//
//  Created by Leslie Helou, Professional Services Engineer, JAMF Software 
//  August 2, 2018
//
//////////////////////////////////////////////////////////////////////////////////////////

import java.io.BufferedReader;
import java.io.BufferedOutputStream;
import java.io.Console;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.FileReader;
import java.net.URL;
import java.net.URLConnection;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.util.Base64;

public class addPrinters {
    
    public static void main(String[] args) throws Exception {
        Console console                 = null;
        String user                     = null;
        String pass                     = null;
        String credentials              = null;
        String printerXml               = null;
        String body                     = null;
        int responseCode                = 0;
        String line                     = null;
        String encoding                 = null;
    
        HttpsURLConnection connection   = null;
        URL endpointUrl                 = null;
        URL authUrl                     = null;
    
        // printer attributes
        String name                     = null;
        String category                 = null;
        String uri                      = null;
        String cupsName                 = null;
        String model                    = null;
        String makeDefault              = null;
    
        // API endpoint for creating printers
        endpointUrl = new URL ("https://your.jamf.server:port/JSSResource/printers/id/0");
    
        // Disable certificate validation - start
        // Ref: http://www.nakov.com/blog/2009/07/16/disable-certificate-validation-in-java-ssl-connections/
        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[] {new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }
                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }
        };
        // Install the all-trusting trust manager
        SSLContext sc = SSLContext.getInstance("TLSv1.2");
        sc.init(null, trustAllCerts, new java.security.SecureRandom()); 
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
    
        // Create all-trusting host name verifier
        HostnameVerifier allHostsValid = new HostnameVerifier() {
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };
        // Install the all-trusting host verifier
        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
        // Disable certificate validation - end
    
        // verify the csv file was passed
        if ( args.length != 1) {
            System.out.println("Missing data file.");
            System.out.println("Usage: addPrinters /path/to/printers.csv");
            System.exit(0);
        }
    
        // verify the file exists
        File tmpFile = new File(args[0]);
        if ( !tmpFile.exists() || tmpFile.isDirectory() ) {
            System.out.println("unable to locate data file: " + args[0]);
            System.exit(0);
        }
    
        // get credentials
        try {
            console = System.console();
    
            // if console is not null
            if (console != null) {
                // read username
                user = console.readLine("Jamf Admin Username: ");
                // read password
                char pwdArray[] = console.readPassword("Password for " + user + ": ");
    
                credentials = user + ":" + new String(pwdArray);
            }
            encoding = Base64.getEncoder().encodeToString((credentials).getBytes("UTF-8"));
    
            // trim off the /id/0 from the endpoint URL and test authentication
            authUrl = new URL (endpointUrl.toString().substring(0, endpointUrl.toString().length() - 5));
            connection = (HttpsURLConnection) authUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setRequestProperty  ("Authorization", "Basic " + encoding);
            connection.setRequestProperty  ("Accept", "application/xml");
    
            responseCode = connection.getResponseCode();
            if ( responseCode == 401 ) {
                System.out.println("Incorrect username and/or password");
                System.exit(0);
            }
        } catch(Exception ex) {
            // if any error occurs
            ex.printStackTrace();
        }
    
        // read the file, create the XML to create the printers, and create them
        try {
            File file = new File(args[0]);
            FileReader fileReader             = new FileReader(file);
            BufferedReader bufferedReader     = new BufferedReader(fileReader);
            StringBuffer stringBuffer         = new StringBuffer();
    
            while ((line = bufferedReader.readLine()) != null) {
                String attribArray[]    = line.split(",[ ]*");
                name         = attribArray[0];
                if ( name == null || name.trim().equals("") ) {
                    System.out.println("Missing printer name: " + line);
                    continue;
                }
                category    = attribArray[1];
                uri         = attribArray[2];
                if ( uri == null || uri.trim().equals("") ) {
                    System.out.println("Missing printer URI: " + uri);
                    continue;
                }
                cupsName    = attribArray[3];
                if ( cupsName == null || cupsName.trim().equals("") ) {
                    System.out.println("Missing printer CUPS_name (queue name): " + cupsName);
                    continue;
                }
                model       = attribArray[4];
                makeDefault = attribArray[5];
    
                printerXml="<?xml version='1.0' encoding='UTF-8'?>" +
                "<printer>" +
                "<name>" + name + "</name>" +
                "<category>" + category + "</category>" +
                "<uri>" + uri + "</uri>" +
                "<CUPS_name>" + cupsName + "</CUPS_name>" +
                "<model>" + model + "</model>" +
                "<make_default>" + makeDefault + "</make_default>" +
                "</printer>";
    
                try {
                    connection = (HttpsURLConnection) endpointUrl.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setDoInput(true);
                    connection.setDoOutput(true);
                    connection.setRequestProperty  ("Authorization", "Basic " + encoding);
                    connection.setRequestProperty  ("Content-Type", "application/xml");
    
                    body = printerXml;
    
                    OutputStream output = new BufferedOutputStream(connection.getOutputStream());
                    output.write(body.getBytes());
                    output.flush();
    
                    InputStream content = (InputStream)connection.getInputStream();
                    BufferedReader in   = new BufferedReader (new InputStreamReader (content));

                    System.out.println("created printer: " + name);
                } catch(Exception e) {
                    System.out.println("***** failed to create printer: " + name);
                }
            }
            fileReader.close();
    
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
