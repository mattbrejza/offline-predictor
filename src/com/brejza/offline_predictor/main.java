package com.brejza.offline_predictor;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import ukhas.Gps_coordinate;
import ukhas.Telemetry_string;;

public class main {
	public static void main(String[] args) {
		//System.out.println("Hello World!" + args.length+ "  " + distanceBetween(52,52,0,-1) + "   " +bearingBetween(52,52,2,1));
		//System.out.println("Hello World!" + args.length+ "  " + distanceBetween(52,2,0,-1) + "   " +bearingBetween(52,52,1,2));
		//System.out.println("Hello World!" + args.length+ "  " + distanceBetween(12,52,0,-1) + "   " +bearingBetween(0,1,0,1));
		
		String flight_d = "";
		String payload_d = "";
		String out_p = "";
		String out_a = "";
		
		if (args.length < 3)
		{
			printHelp();
			return;
		}
		else
		{
			flight_d = args[0];
			payload_d = args[1];
			out_p = args[2];
			if (args.length > 3)
			{
				out_a = args[3];
			}
		}

		for (int i = 0; i < args.length; i++)
		{
			//System.out.println(args[i]);
		}
		List<String> strings = getFlightData(flight_d,payload_d);
		
		if (strings.size() < 5)
		{
			System.out.println("No data returned for this payload/flight ID");
			return;
		}
		
		double[] alts = new double[strings.size()];
		double[] speed = new double[strings.size()];
		double[] heading = new double[strings.size()];
		int index = 0;
		Gps_coordinate last_co = null;
		long last_time = 0;
		double max_alt=0;
		
		//we dont actually want to start from the highest alitude, rather the packet after the highest
		long last_timep1=0;
		Gps_coordinate last_cop1 = null;
		
		for (int i = 0; i< strings.size()-1;i++)
		{
			
			
			
			Telemetry_string s = new Telemetry_string(strings.get(i),null);
			if (s.coords != null){
				if (s.coords.alt_valid && s.coords.latlong_valid)
				{
					//System.out.println(s.time.toString()+","+s.coords.latitude+","+s.coords.longitude+","+s.coords.altitude);
					if (last_co != null)
					{
						if (s.coords.altitude > max_alt)
						{
							max_alt = s.coords.altitude;
							alts[index] = s.coords.altitude;
							heading[index] = bearingBetween(last_co.latitude,s.coords.latitude,last_co.longitude,s.coords.longitude);
							long time = s.time.getTime()-last_time;
							if (time>0)
								speed[index] = (double)1e6 * distanceBetween(last_co.latitude,s.coords.latitude,last_co.longitude,s.coords.longitude)/time;
							index++;
							last_co = s.coords;
							last_time=s.time.getTime();
						}
						else
						{
							if (last_cop1 == null)
							{
								last_cop1 = s.coords;
								last_timep1 = s.time.getTime();
							}
							else if (s.coords.altitude > last_cop1.altitude)
							{
								last_cop1 = s.coords;
								last_timep1 = s.time.getTime();
							}
						}
					}
					else
					{
						last_co = s.coords;
						last_time=s.time.getTime();
					}
				}
			}
		}
		
		for (int i = 0; i < index; i++)
		{
			//System.out.println(alts[i]+"  "+heading[i]+"  "+speed[i]);
		}
		
		//open files for writing
		PrintWriter writer1;
		//PrintWriter writer2;
		try {
			writer1 = new PrintWriter(out_p, "UTF-8");
			//writer2 = new PrintWriter(out_a, "UTF-8");
			
			writer1.println("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>");
			writer1.println("<gpx");
			writer1.println("Version=\"1.0\"");
			writer1.println("creator=\"offine-predictor - M. Brejza\"");
			writer1.println("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
			writer1.println("xmlns=\"http://www.topografix.com/GPX/1/0\"");
			writer1.println("xsi:schemaLocation = \"http://www.topografix.com/GPX/1/0 http://www.topografix.com/GPX/1/0/gpx.xsd\" >");
			
			writer1.println("<trk>");
			writer1.println("<trkseg>");
			
			
			System.out.println("time,latitude,longitude,altitude");
			
			
			//time to do some predicting
			double lat = last_cop1.latitude;
			double lon = last_cop1.longitude;
			double alt = last_cop1.altitude;
			last_time = last_timep1;
			double timestep = 1;
			double d_time = 0;
			//2011-9-18T00:32:34Z
			SimpleDateFormat ft = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss'Z'");
			
			double sincelastwrite = 0;
			
			double drag = 7.3;//12.5;
			
			Date d = new Date((long)d_time*1000+last_time);
			System.out.println(d.toString()+","+lat + "," + lon + "," + alt);
			writer1.println("<trkpt lat=\"" + lat + "\" lon=\"" + lon + "\">");
			writer1.println("<ele>" + alt + "</ele>");
			writer1.println("<time>" +  ft.format(d) + "</time>");
			writer1.println("</trkpt>");
			
			while(alt > 0)
			{
				double w_x,w_y,dd_x,dd_y;
				double rate = getDescentRate(alt,drag);
				alt = alt - rate*timestep;
				d_time+= timestep;
				
				double[] wind = getXYWind(alt,alts,speed,heading,index);
				w_x=wind[0];
				w_y=wind[1];
				double[]dd = _get_frame(lat, lon, alt);
				dd_x=dd[0];
				dd_y=dd[1];
				
				lat = lat + w_x*timestep/dd_x;
				lon = lon + w_y*timestep/dd_y;
				
				
				sincelastwrite += timestep;
				
				
				if (sincelastwrite>30){
					sincelastwrite = 0;
					d = new Date((long)d_time*1000+last_time);
					System.out.println(d.toString()+","+lat + "," + lon + "," + alt);
					writer1.println("<trkpt lat=\"" + lat + "\" lon=\"" + lon + "\">");
					writer1.println("<ele>" + alt + "</ele>");
					writer1.println("<time>" +  ft.format(d) + "</time>");
					writer1.println("</trkpt>");
				}
			}
			d = new Date((long)d_time*1000+last_time);
			writer1.println("<trkpt lat=\"" + lat + "\" lon=\"" + lon + "\">");
			writer1.println("<ele>" + alt + "</ele>");
			writer1.println("<time>" +  ft.format(d) + "</time>");
			writer1.println("</trkpt>");
			
			
			writer1.println("</trkseg>");
			writer1.println("</trk>");
			writer1.println("</gpx>");
			
			writer1.close();
			//writer2.close();
		
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		writeGPX(out_a,strings);
		
		
	}
	
	public static List<String> getFlightData(String flightID, String payloadID)
	{
		
		List<String> out = new ArrayList<String>();
		
		URL url;
	    InputStream is = null;
	    BufferedReader br;
	    String line;
		try {
	        
			url = new URL("http://habitat.habhub.org/habitat/_design/ept/_list/csv/payload_telemetry/flight_payload_time?include_docs=true&startkey=[%22"+flightID+"%22,%22"+payloadID+"%22]&endkey=[%22"+flightID+"%22,%22"+payloadID+"%22,[]]&fields=_sentence");
			//url = new URL("http://habitat.habhub.org");
	        
			is = url.openStream();  // throws an IOException
	        br = new BufferedReader(new InputStreamReader(is));

	        while ((line = br.readLine()) != null) {
	        	if (line.length() > 3){
		        	line = line.substring(1, line.length()-1);
		            //System.out.println(line);
		            out.add(line);
	        	}
	        }
	        return out;
	    } catch (MalformedURLException mue) {
	         mue.printStackTrace();
	    } catch (IOException ioe) {
	         ioe.printStackTrace();
	    } finally {
	        try {
	            is.close();
	        } catch (IOException ioe) {
	            // nothing to see here
	        }
	    }
		return null;
	}

	public static double getDescentRate(double alt, double drag_coeff)
	{		
		return drag_coeff/Math.sqrt(get_density(alt));
	}
	
	public static void printHelp()
	{
		
		System.out.println("Usage: flight_ID payload_ID prediction_filename <actual_track_filename>");
	}
	
	public static void writeGPX(String filename, List<String> data)
	{
		if (filename == "")
			return;
		
		PrintWriter writer1;
		
		try {
			writer1 = new PrintWriter(filename, "UTF-8");
			
			writer1.println("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>");
			writer1.println("<gpx");
			writer1.println("Version=\"1.0\"");
			writer1.println("creator=\"offine-predictor - M. Brejza\"");
			writer1.println("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
			writer1.println("xmlns=\"http://www.topografix.com/GPX/1/0\"");
			writer1.println("xsi:schemaLocation = \"http://www.topografix.com/GPX/1/0 http://www.topografix.com/GPX/1/0/gpx.xsd\" >");
			
			writer1.println("<trk>");
			writer1.println("<trkseg>");
			SimpleDateFormat ft = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss'Z'");
			for (int i = 0; i < data.size(); i++)
			{
				Telemetry_string s = new Telemetry_string(data.get(i),null);
				
				if (s != null){
					if (s.coords != null)
					{
						
					
				
						writer1.println("<trkpt lat=\"" + s.coords.latitude + "\" lon=\"" + s.coords.longitude + "\">");
						writer1.println("<ele>" + s.coords.altitude + "</ele>");
						writer1.println("<time>" +  ft.format(s.time) + "</time>");
						writer1.println("</trkpt>");
					}
				}
			}
			
			
			writer1.println("</trkseg>");
			writer1.println("</trk>");
			writer1.println("</gpx>");
			
			writer1.close();
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		
	}
	
	// Get the distance (in metres) of one degree of latitude and one degree of
	// longitude. This varys with height (not much grant you).
	public static double[] _get_frame(double lat, double lng, double alt)
	{
		double theta, r;

	    theta = 2.f * Math.PI * (90.f - lat) / 360.f;
	    r = 6371009.f + alt;

	    // See the differentiation section of
	    // http://en.wikipedia.org/wiki/Spherical_coordinate_system

	    // d/dv = d/dlat = -d/dtheta
	    double d_dlat = (2.f * Math.PI) * r / 360.f;

	    // d/du = d/dlong = d/dphi
	    double d_dlng = (2.f * Math.PI) * r * Math.sin(theta) / 360.f;
	    
	    double[] out = new double[2];
	    out[1] = d_dlng;
	    out[0] = d_dlat;
	    return out;
	}
	
	public static double[] getXYWind(double alt, double[] alts, double[] speed, double[] heading, int count)
	{
		double s, h;
		//binary search
		int low = 0;
	    int high = count-1;
	    int middle=0;

	    while(low <= high) {
	    	middle = (low+high) /2; 
	    	if (alt> alts[middle]){
	    		low = middle +1;
	    	} else if (alt< alts[middle]){
	    		high = middle -1;
	    	} else { // The element has been found
	    		break;
	    	}
	    }
	    
	    //now check which of i+1, i i-1 is closest
	    double d1 = 1e8,d2 = 1e8,d3 = 1e8;
	    if (middle-1 >= 0)
	    	d1 = Math.abs(alt-alts[middle-1]);
	    if (middle+1 < count)
	    	d2 = Math.abs(alt-alts[middle+1]);
	    d3 = Math.abs(alt-alts[middle]);
	    
	    if (d1 < d2 && d1 < d3)
	    {
	    	s=speed[middle-1];
	    	h=heading[middle-1];
	    }
	    else if(d2<d3 && d2<d1)
	    {
	    	s=speed[middle+1];
	    	h=heading[middle+1];
	    }
	    else
	    {
	    	s=speed[middle];
	    	h=heading[middle];
	    }
	    
	    double[] out = new double[2];
	    
	    out[0] = s*Math.cos(h/((double)180)*Math.PI); //x
	    out[1] = s*Math.sin(h/((double)180)*Math.PI);
		return out;
	}
	
	public static double get_density(double altitude)
	{
	    
		double temp = 0.f, pressure = 0.f;
	    
	    if (altitude > 25000) {
	        temp = -131.21 + 0.00299 * altitude;
	        pressure = 2.488*Math.pow((temp+273.1)/216.6,-11.388);
	    }
	    if (altitude <=25000 && altitude > 11000) {
	        temp = -56.46;
	        pressure = 22.65 * Math.exp(1.73-0.000157*altitude);
	    }
	    if (altitude <=11000) {
	        temp = 15.04 - 0.00649 * altitude;
	        pressure = 101.29 * Math.pow((temp + 273.1)/288.08,5.256);
	    }
	    
	    return pressure/(0.2869*(temp+273.1));
	}
	
	public static double distanceBetween(double lat1,double lat2,double lon1, double lon2)
	{
		double R = 6371; // km
		double torad = 1/(double)180*Math.PI;
		double dLat = (lat2-lat1)*torad;
		double dLon = (lon2-lon1)*torad;
		lat1 = lat1*torad;
		lat2 = lat2*torad;

		double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
		        Math.sin(dLon/2) * Math.sin(dLon/2) * Math.cos(lat1) * Math.cos(lat2); 
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a)); 
		return R * c;
		
	}
	
	public static double bearingBetween(double lat1,double lat2,double lon1, double lon2)
	{
		double torad = 1/(double)180*Math.PI;
		double todeg = (double)180/Math.PI;
		double dLon = (lon2-lon1)*torad;
		lat1 = lat1*torad;
		lat2 = lat2*torad;
		double y = Math.sin(dLon) * Math.cos(lat2);
		double x = Math.cos(lat1)*Math.sin(lat2) - Math.sin(lat1)*Math.cos(lat2)*Math.cos(dLon);
		return  ((double)360+Math.atan2(y,x)*todeg)%360;
		
	}
	
}
